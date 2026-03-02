package org.keycloak.gh.bot.security.email;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.model.Message;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class MailProcessor {

    private static final Logger LOGGER = Logger.getLogger(MailProcessor.class);
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?m)^--\\s*$|^You received this message because you are subscribed.*");

    @ConfigProperty(name = "google.group.target")
    String targetGroupEmail;

    @ConfigProperty(name = "gmail.user.email")
    String botEmail;

    @ConfigProperty(name = "repository.privateRepository")
    String repositoryName;

    @Inject
    GmailAdapter gmail;

    @Inject
    GitHubInstallationProvider gitHubInstallationProvider;

    private TargetGroup targetGroup;

    private final Cache<String, GHIssue> issueCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofHours(1))
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
            var subject = headers.getOrDefault("Subject", "(No Subject)").trim();
            var body = sanitizeBody(gmail.getBody(msg)).orElse("(No content)");
            var attachments = gmail.getAttachments(msg);

            var attachmentSection = buildAttachmentSection(attachments, messageId);

            var issueOpt = resolveIssue(github, threadId);

            if (issueOpt.isPresent()) {
                var issue = issueOpt.get();
                if (issue.getState() == GHIssueState.CLOSED) {
                    issue.reopen();
                    LOGGER.infof("Reopened existing closed issue #%d for thread %s", issue.getNumber(), threadId);
                }
                appendComment(issue, from, subject, body, threadId, attachmentSection);
            } else {
                var newIssue = createNewIssue(repository, threadId, subject, from, body, attachmentSection);
                // Explicitly cache the newly created issue so subsequent loop iterations instantly find it
                issueCache.put(threadId, newIssue);
            }

            gmail.markAsRead(msgSummary.getId());
        } catch (Exception e) {
            handleProcessingFailure(msgSummary.getId(), e);
        }
    }

    private Optional<GHIssue> resolveIssue(GitHub github, String threadId) {
        GHIssue cachedOrFetchedIssue = issueCache.get(threadId, id -> {
            try {
                var query = "repo:%s \"%s\" label:%s is:issue".formatted(repositoryName, id, Labels.SOURCE_EMAIL);
                var iterator = github.searchIssues().q(query).list().iterator();
                return iterator.hasNext() ? iterator.next() : null;
            } catch (Exception e) {
                LOGGER.warnf(e, "GitHub search failed for thread %s", id);
                return null;
            }
        });

        return Optional.ofNullable(cachedOrFetchedIssue);
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

    private void appendComment(GHIssue issue, String from, String subject, String body, String threadId, String attachmentSection) throws IOException {
        issue.comment(formatGitHubComment(threadId, from, subject, body, attachmentSection));
    }

    private GHIssue createNewIssue(GHRepository repo, String threadId, String subject, String from, String body, String attachmentSection) throws IOException {
        var issue = repo.createIssue(subject).body(Constants.ISSUE_DESCRIPTION_TEMPLATE).create();
        issue.addLabels(Labels.STATUS_TRIAGE, Labels.SOURCE_EMAIL);
        issue.comment(formatGitHubComment(threadId, from, subject, body, attachmentSection));
        return issue;
    }

    private String formatGitHubComment(String threadId, String from, String subject, String body, String attachmentSection) {
        return "%s %s\nSubject: %s\nFrom: %s\n\n%s%s".formatted(
                Constants.GMAIL_THREAD_ID_PREFIX, threadId, subject, from, body, attachmentSection
        );
    }

    private boolean isFromBot(String from) {
        return from != null && from.toLowerCase().contains(botEmail.toLowerCase());
    }

    private boolean isValidGroupMessage(Map<String, String> headers) {
        if (targetGroup.matchesListId(headers.get("List-ID"))) {
            return true;
        }
        var to = headers.get("To");
        var cc = headers.get("Cc");
        return (to != null && to.contains(targetGroup.email())) || (cc != null && cc.contains(targetGroup.email()));
    }

    private Optional<String> sanitizeBody(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        var matcher = SIGNATURE_PATTERN.matcher(body);
        var content = matcher.find() ? body.substring(0, matcher.start()) : body;
        return content.isBlank() ? Optional.empty() : Optional.of(content.strip());
    }
}