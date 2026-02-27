package org.keycloak.gh.bot.security.email;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.model.Message;
import io.quarkiverse.githubapp.GitHubClientProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
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
    GitHubClientProvider gitHubClientProvider;

    private TargetGroup targetGroup;

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
            var github = resolveGitHubClient();
            var repository = github.getRepository(repositoryName);
            var query = "is:unread -from:%s".formatted(botEmail);
            var messages = gmail.fetchUnreadMessages(query);

            LOGGER.infof("Email sync triggered. Checking for new messages in %s.", targetGroup.email());
            for (var msgSummary : messages) {
                processSingleMessage(msgSummary, github, repository);
                LOGGER.infof("Fetching message %s from %s.", msgSummary.getThreadId(), targetGroup.email());
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
            var issueOpt = findEmailIssueByThreadId(github, threadId);

            if (issueOpt.isPresent()) {
                var issue = issueOpt.get();
                if (issue.getState() == GHIssueState.CLOSED) {
                    issue.reopen();
                    LOGGER.infof("Reopened existing closed issue #%d for thread %s", issue.getNumber(), threadId);
                }
                appendComment(issue, from, subject, body, threadId, attachmentSection);
            } else {
                createNewIssue(repository, threadId, subject, from, body, attachmentSection);
            }

            gmail.markAsRead(msgSummary.getId());
        } catch (Exception e) {
            handleProcessingFailure(msgSummary.getId(), e);
        }
    }

    private GitHub resolveGitHubClient() throws IOException {
        var appClient = gitHubClientProvider.getApplicationClient();
        var installationId = appClient.getApp().listInstallations().iterator().next().getId();
        return gitHubClientProvider.getInstallationClient(installationId);
    }

    private Optional<GHIssue> findEmailIssueByThreadId(GitHub github, String threadId) {
        // Removed "is:open" to ensure we find closed issues belonging to this thread as well
        var query = "repo:%s \"%s\" label:%s is:issue".formatted(repositoryName, threadId, Labels.SOURCE_EMAIL);
        var iterator = github.searchIssues().q(query).list().iterator();
        return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
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
        var level = (e instanceof GoogleJsonResponseException ge && ge.getStatusCode() >= 400 && ge.getStatusCode() < 500) ? Logger.Level.WARN : Logger.Level.ERROR;
        LOGGER.logf(level, e, "Failure processing message %s. Marking read to prevent retry loop.", messageId);
        try {
            gmail.markAsRead(messageId);
        } catch (IOException ex) {
            LOGGER.errorf(ex, "Failed to mark message %s as read.", messageId);
        }
    }

    private void appendComment(GHIssue issue, String from, String subject, String body, String threadId, String attachmentSection) throws IOException {
        issue.comment(formatGitHubComment(threadId, from, subject, body, attachmentSection));
    }

    private void createNewIssue(GHRepository repo, String threadId, String subject, String from, String body, String attachmentSection) throws IOException {
        var issue = repo.createIssue(subject).body(Constants.ISSUE_DESCRIPTION_TEMPLATE).create();
        issue.addLabels(Labels.STATUS_TRIAGE, Labels.SOURCE_EMAIL);
        issue.comment(formatGitHubComment(threadId, from, subject, body, attachmentSection));
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