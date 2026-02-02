package org.keycloak.gh.bot.security.email;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.model.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.common.GitHubAdapter;
import org.keycloak.gh.bot.utils.Labels;
import org.keycloak.gh.bot.utils.Throttler;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MailProcessor {

    private static final Logger LOG = Logger.getLogger(MailProcessor.class);
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?m)^--\\s*$|^You received this message because you are subscribed.*");

    @ConfigProperty(name = "google.group.target") String targetGroup;
    @ConfigProperty(name = "gmail.user.email") String botEmail;

    @ConfigProperty(name = "email.sender.secalert") String secAlertEmail;
    @ConfigProperty(name = "email.sender.jira", defaultValue = "jira-issues@redhat.com") String jiraSenderEmail;

    @Inject GmailAdapter gmail;
    @Inject GitHubAdapter github;
    @Inject Throttler throttler;

    public void processUnreadEmails() {
        if (github.isAccessDenied()) return;

        String query = "is:unread -from:" + botEmail;
        List<Message> messages;

        try {
            messages = gmail.fetchUnreadMessages(query);
        } catch (IOException e) {
            LOG.warnf("Failed to fetch unread messages: %s", e.getMessage());
            return;
        }

        for (Message msgSummary : messages) {
            if (processMessage(msgSummary)) {
                throttler.throttle(Duration.ofSeconds(1));
            }
        }
    }

    private boolean processMessage(Message msgSummary) {
        try {
            Message msg = gmail.getMessage(msgSummary.getId());
            Map<String, String> headers = gmail.getHeadersMap(msg);
            String from = headers.getOrDefault("From", "");

            if (isFromBot(from) || !isValidGroupMessage(headers)) {
                gmail.markAsRead(msgSummary.getId());
                return true;
            }

            String threadId = msg.getThreadId();

            String subject = headers.getOrDefault("Subject", "").trim();
            if (subject.isEmpty()) {
                subject = "(No Subject)";
            }

            String cleanBody = sanitizeBody(gmail.getBody(msg)).orElse("(No content)");

            Optional<GHIssue> existingIssue = github.findOpenEmailIssueByThreadId(threadId);

            if (existingIssue.isPresent()) {
                GHIssue issue = existingIssue.get();
                processCveUpdates(issue, from, cleanBody);
                appendComment(issue, from, cleanBody, threadId);
            } else {
                createNewIssue(threadId, subject, from, cleanBody);
            }

            gmail.markAsRead(msgSummary.getId());
            return true;
        } catch (Exception e) {
            handleProcessingError(msgSummary.getId(), e);
            return true;
        }
    }

    private void processCveUpdates(GHIssue issue, String from, String body) throws IOException {
        boolean isSecAlert = from != null && secAlertEmail != null && from.contains(secAlertEmail);
        boolean isJira = from != null && jiraSenderEmail != null && from.contains(jiraSenderEmail);

        if (isSecAlert || isJira) {
            Matcher matcher = Constants.CVE_PATTERN.matcher(body);
            if (matcher.find()) {
                String newCveId = matcher.group();
                String currentTitle = issue.getTitle();

                if (currentTitle != null) {
                    if (currentTitle.contains(Constants.CVE_TBD_PREFIX)) {
                        String newTitle = currentTitle.replace(Constants.CVE_TBD_PREFIX, newCveId);
                        github.updateTitleAndLabels(issue, newTitle, Constants.KIND_CVE);
                    } else {
                        Matcher titleMatcher = Constants.CVE_PATTERN.matcher(currentTitle);
                        if (titleMatcher.find()) {
                            String oldCveId = titleMatcher.group();
                            if (!oldCveId.equals(newCveId)) {
                                String newTitle = currentTitle.replace(oldCveId, newCveId);
                                github.updateTitleAndLabels(issue, newTitle, Constants.KIND_CVE);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleProcessingError(String id, Exception e) {
        if (e instanceof GoogleJsonResponseException ge && ge.getStatusCode() >= 400 && ge.getStatusCode() < 500) {
            LOG.errorf(e, "Unrecoverable error on message %s.", id);
        } else {
            LOG.errorf(e, "Unexpected error on message %s. Marking read to prevent poison loop.", id);
        }
        silentlyMarkAsRead(id);
    }

    private void silentlyMarkAsRead(String id) {
        try {
            gmail.markAsRead(id);
        } catch (IOException ex) {
            LOG.errorf("Failed to mark poison message %s as read.", id);
        }
    }

    private void appendComment(GHIssue issue, String from, String body, String threadId) throws IOException {
        github.commentOnIssue(issue, "**Reply from " + from + ":**\n\n" + body);
        LOG.debugf("✅ Commented on #%d (Thread %s)", issue.getNumber(), threadId);
    }

    private void createNewIssue(String threadId, String subject, String from, String body) throws IOException {
        GHIssue newIssue = github.createSecurityIssue(subject, Constants.ISSUE_DESCRIPTION_TEMPLATE, Constants.SOURCE_EMAIL);
        if (newIssue != null) {
            try {
                newIssue.addLabels(Labels.STATUS_TRIAGE);
            } catch (IOException e) {
                LOG.errorf(e, "Failed to label issue #%d", newIssue.getNumber());
            }
            github.commentOnIssue(newIssue, Constants.GMAIL_THREAD_ID_PREFIX + " " + threadId + "\n**From:** " + from + "\n\n" + body);
        }
    }

    private boolean isFromBot(String from) {
        return from != null && from.toLowerCase().contains(botEmail.toLowerCase());
    }

    private boolean isValidGroupMessage(Map<String, String> headers) {
        String listId = headers.get("List-ID");
        String groupIdentifier = targetGroup.split("@")[0];
        if (listId != null && listId.contains(groupIdentifier)) return true;
        String to = headers.get("To");
        String cc = headers.get("Cc");
        return (to != null && to.contains(targetGroup)) || (cc != null && cc.contains(targetGroup));
    }

    private Optional<String> sanitizeBody(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        Matcher matcher = SIGNATURE_PATTERN.matcher(body);
        if (matcher.find()) {
            String trimmed = body.substring(0, matcher.start()).trim();
            return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
        }
        return Optional.of(body.trim());
    }
}