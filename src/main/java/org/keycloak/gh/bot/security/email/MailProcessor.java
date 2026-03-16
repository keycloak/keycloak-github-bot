package org.keycloak.gh.bot.security.email;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.services.gmail.model.Message;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MailProcessor {

    private static final Logger LOGGER = Logger.getLogger(MailProcessor.class);
    private static final Pattern BRACKET_PREFIX_PATTERN = Pattern.compile("^.*?\\[.*?]\\s*");
    private static final Pattern REPLY_PREFIX_PATTERN = Pattern.compile("^(?:\\s*(?:Re|Fwd|Fw)\\s*:\\s*)+", Pattern.CASE_INSENSITIVE);

    @ConfigProperty(name = "google.group.target")
    String targetGroupEmail;

    @ConfigProperty(name = "gmail.user.email")
    String botEmail;

    @ConfigProperty(name = "repository.privateRepository")
    String repositoryName;

    @ConfigProperty(name = "email.sender.secalert")
    String secAlertEmail;

    @Inject
    GmailAdapter gmail;

    @Inject
    GitHubInstallationProvider gitHubInstallationProvider;

    @Inject
    EmailBodySanitizer bodySanitizer; // Extracted parsing logic dependency

    private TargetGroup targetGroup;

    private final Cache<String, Integer> issueCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofDays(7))
            .build();

    @PostConstruct
    void init() {
        this.targetGroup = TargetGroup.from(targetGroupEmail);
    }

    public void processUnreadEmails() {
        if (repositoryName == null || repositoryName.isBlank()) {
            LOGGER.error("Configuration error: repository.privateRepository is missing");
            return;
        }

        try {
            var github = gitHubInstallationProvider.getGitHubClient(repositoryName);
            var repository = github.getRepository(repositoryName);
            var query = "is:unread -from:%s".formatted(botEmail);

            // Gmail returns messages in descending order
            var messages = gmail.fetchUnreadMessages(query);

            // Reverse the list to process chronologically (oldest first)
            var chronologicalMessages = new java.util.ArrayList<>(messages);
            java.util.Collections.reverse(chronologicalMessages);

            LOGGER.infof("Email sync triggered. Checking for new messages in %s.", targetGroup.email());
            for (var msgSummary : chronologicalMessages) {
                LOGGER.infof("Fetching message %s from %s.", msgSummary.getThreadId(), targetGroup.email());
                processSingleMessage(msgSummary, github, repository);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to synchronize emails with GitHub", e);
        }
    }

    private void processSingleMessage(Message msgSummary, GitHub github, GHRepository repository) {
        try {
            var msg = gmail.getMessage(msgSummary.getId());
            var headers = gmail.getHeadersMap(msg);
            var from = headers.getOrDefault("From", "");

            if (isFromBot(from) || !isValidGroupMessage(headers)) {
                gmail.markAsRead(msgSummary.getId());
                return;
            }

            var threadId = msg.getThreadId();
            var messageId = headers.getOrDefault("Message-ID", "").replaceAll("^<|>$", "");
            var subject = normalizeSubject(headers.getOrDefault("Subject", "(No Subject)").trim());

            var body = bodySanitizer.sanitize(gmail.getBody(msg)).orElse("(No content)");
            var attachments = gmail.getAttachments(msg);

            var attachmentSection = buildAttachmentSection(attachments, messageId);

            var issueOpt = resolveIssue(github, repository, threadId);

            if (issueOpt.isEmpty() && isFromSecAlert(from)) {
                issueOpt = resolveIssueBySecAlertThread(github, repository, threadId);
            }

            if (issueOpt.isPresent()) {
                var issue = issueOpt.get();
                if (issue.getState() == GHIssueState.CLOSED) {
                    issue.reopen();
                    LOGGER.infof("Reopened existing closed issue #%d for thread %s", issue.getNumber(), threadId);
                }
                appendComment(issue, from, body, attachmentSection);

                if (isFromSecAlert(from)) {
                    applyCveIdFromSecAlert(issue, subject, body);
                }
            } else {
                var newIssue = createNewIssue(repository, threadId, subject, from, body, attachmentSection);
                issueCache.put(threadId, newIssue.getNumber());
                LOGGER.infof("Creating new issue #%d for thread %s", newIssue.getNumber(), threadId);
            }

            gmail.markAsRead(msgSummary.getId());
        } catch (Exception e) {
            handleProcessingFailure(msgSummary.getId(), e);
        }
    }

    private Optional<GHIssue> resolveIssue(GitHub github, GHRepository repository, String threadId) {
        Integer issueNumber = issueCache.get(threadId, id -> {
            try {
                var query = "repo:%s \"%s\" label:%s is:issue in:comments".formatted(repositoryName, id, Labels.SOURCE_EMAIL);
                var iterator = github.searchIssues().q(query).list().iterator();
                return iterator.hasNext() ? iterator.next().getNumber() : null;
            } catch (Exception e) {
                LOGGER.warnf(e, "GitHub search failed for thread %s", id);
                return null;
            }
        });

        if (issueNumber != null) {
            try {
                return Optional.ofNullable(repository.getIssue(issueNumber));
            } catch (IOException e) {
                LOGGER.warnf(e, "Failed to fetch fresh issue #%d from GitHub", issueNumber);
            }
        }

        return Optional.empty();
    }

    private String buildAttachmentSection(List<GmailAdapter.Attachment> attachments, String messageId) {
        if (attachments.isEmpty()) {
            return "";
        }

        var links = new StringBuilder("\n\n**Attachments:**\n");
        for (var att : attachments) {
            links.append("- %s\n".formatted(att.fileName()));
        }

        if (!messageId.isBlank()) {
            links.append(Constants.ATTACHMENTS_FOOTER.formatted(targetGroup.getArchiveLink(messageId)));
        }

        return links.toString();
    }

    private void handleProcessingFailure(String messageId, Exception e) {
        LOGGER.errorf(e, "Failure processing message %s. It will remain unread and be retried.", messageId);
    }

    private void appendComment(GHIssue issue, String from, String body, String attachmentSection) throws IOException {
        issue.comment(formatReplyComment(from, body, attachmentSection));
    }

    private GHIssue createNewIssue(GHRepository repo, String threadId, String subject, String from, String body, String attachmentSection) throws IOException {
        var issue = repo.createIssue(subject).body(Constants.ISSUE_DESCRIPTION_TEMPLATE).create();
        issue.addLabels(Labels.STATUS_TRIAGE, Labels.SOURCE_EMAIL);
        issue.comment(formatNewIssueComment(threadId, subject, from, body, attachmentSection));
        return issue;
    }

    static String formatNewIssueComment(String threadId, String subject, String from, String body, String attachmentSection) {
        return "%s %s\nSubject: %s\nFrom: %s\n\n---\n\n%s%s".formatted(
                Constants.GMAIL_THREAD_ID_PREFIX, threadId, subject, from, body, attachmentSection
        );
    }

    static String formatReplyComment(String from, String body, String attachmentSection) {
        return "From: %s\n\n---\n\n%s%s".formatted(from, body, attachmentSection);
    }

    static String normalizeSubject(String subject) {
        String result = BRACKET_PREFIX_PATTERN.matcher(subject).replaceFirst("");
        return REPLY_PREFIX_PATTERN.matcher(result).replaceFirst("");
    }

    private boolean isFromBot(String from) {
        return from != null && from.toLowerCase().contains(botEmail.toLowerCase());
    }

    private boolean isFromSecAlert(String from) {
        return from != null && from.toLowerCase().contains(secAlertEmail.toLowerCase());
    }

    private Optional<GHIssue> resolveIssueBySecAlertThread(GitHub github, GHRepository repository, String threadId) {
        try {
            var query = "repo:%s \"%s %s\" is:issue in:comments".formatted(
                    repositoryName, Constants.SECALERT_THREAD_ID_PREFIX, threadId);
            var iterator = github.searchIssues().q(query).list().iterator();
            if (iterator.hasNext()) {
                int issueNumber = iterator.next().getNumber();
                return Optional.ofNullable(repository.getIssue(issueNumber));
            }
        } catch (Exception e) {
            LOGGER.warnf(e, "GitHub search failed for SecAlert thread %s", threadId);
        }
        return Optional.empty();
    }

    void applyCveIdFromSecAlert(GHIssue issue, String subject, String body) throws IOException {
        String cveId = extractCveId(subject);
        if (cveId == null) {
            cveId = extractCveId(body);
        }
        if (cveId == null) return;

        String title = issue.getTitle();
        if (title != null && title.startsWith(Constants.CVE_TBD_PREFIX)) {
            String newTitle = title.replace(Constants.CVE_TBD_PREFIX, "[" + cveId + "]");
            issue.setTitle(newTitle);
            LOGGER.infof("Replaced %s with [%s] in issue #%d", Constants.CVE_TBD_PREFIX, cveId, issue.getNumber());

            var labelNames = issue.getLabels().stream()
                    .map(GHLabel::getName)
                    .toList();

            if (labelNames.contains(Status.CVE_REQUEST.toLabel())) {
                issue.removeLabels(Status.CVE_REQUEST.toLabel());
                LOGGER.infof("Removed %s label from issue #%d", Status.CVE_REQUEST.toLabel(), issue.getNumber());
            }

            if (!labelNames.contains(Kind.CVE.toLabel())) {
                issue.addLabels(Kind.CVE.toLabel());
                LOGGER.infof("Added %s label to issue #%d", Kind.CVE.toLabel(), issue.getNumber());
            }
        }
    }

    static String extractCveId(String text) {
        if (text == null) return null;
        Matcher matcher = Constants.CVE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private boolean isValidGroupMessage(Map<String, String> headers) {
        if (targetGroup.matchesListId(headers.get("List-ID"))) {
            return true;
        }
        var to = headers.get("To");
        var cc = headers.get("Cc");
        return (to != null && to.contains(targetGroup.email())) || (cc != null && cc.contains(targetGroup.email()));
    }
}