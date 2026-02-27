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
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class MailProcessor {

    private static final Logger LOGGER = Logger.getLogger(MailProcessor.class);
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?m)^--\\s*$|^You received this message because you are subscribed.*");

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    @ConfigProperty(name = "gmail.user.email")
    String botEmail;

    @ConfigProperty(name = "repository.privateRepository")
    String repositoryName;

    @Inject
    GmailAdapter gmail;

    @Inject
    GitHubClientProvider gitHubClientProvider;

    private String targetGroupId;

    @PostConstruct
    void init() {
        this.targetGroupId = targetGroup.split("@")[0];
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

            LOGGER.infof("Email sync triggered. Checking for new messages in %s.", targetGroup);
            for (var msgSummary : messages) {
                processSingleMessage(msgSummary, github, repository);
                LOGGER.infof("Fetching message %s from %s.", msgSummary.getThreadId(), targetGroup);
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
            var subject = headers.getOrDefault("Subject", "(No Subject)").trim();
            var body = sanitizeBody(gmail.getBody(msg)).orElse("(No content)");

            var issue = findOpenEmailIssueByThreadId(github, threadId);

            if (issue.isPresent()) {
                appendComment(issue.get(), from, subject, body, threadId);
            } else {
                createNewIssue(repository, threadId, subject, from, body);
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

    private Optional<GHIssue> findOpenEmailIssueByThreadId(GitHub github, String threadId) {
        var query = "repo:%s \"%s\" label:%s is:open is:issue".formatted(repositoryName, threadId, Labels.SOURCE_EMAIL);
        var iterator = github.searchIssues().q(query).list().iterator();
        return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
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

    private void appendComment(GHIssue issue, String from, String subject, String body, String threadId) throws IOException {
        issue.comment(formatGitHubComment(threadId, from, subject, body));
    }

    private void createNewIssue(GHRepository repo, String threadId, String subject, String from, String body) throws IOException {
        var issue = repo.createIssue(subject).body(Constants.ISSUE_DESCRIPTION_TEMPLATE).create();
        issue.addLabels(Labels.STATUS_TRIAGE, Labels.SOURCE_EMAIL);
        issue.comment(formatGitHubComment(threadId, from, subject, body));
    }

    private String formatGitHubComment(String threadId, String from, String subject, String body) {
        return "%s %s\nSubject: %s\nFrom: %s\n\n%s".formatted(
                Constants.GMAIL_THREAD_ID_PREFIX, threadId, subject, from, body
        );
    }

    private boolean isFromBot(String from) {
        return from != null && from.toLowerCase().contains(botEmail.toLowerCase());
    }

    private boolean isValidGroupMessage(Map<String, String> headers) {
        var listId = headers.get("List-ID");
        if (listId != null && listId.contains(targetGroupId)) return true;
        var to = headers.get("To");
        var cc = headers.get("Cc");
        return (to != null && to.contains(targetGroup)) || (cc != null && cc.contains(targetGroup));
    }

    private Optional<String> sanitizeBody(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        var matcher = SIGNATURE_PATTERN.matcher(body);
        var content = matcher.find() ? body.substring(0, matcher.start()) : body;
        return content.isBlank() ? Optional.empty() : Optional.of(content.strip());
    }
}