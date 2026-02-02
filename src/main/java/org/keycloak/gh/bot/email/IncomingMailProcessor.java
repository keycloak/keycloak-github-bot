package org.keycloak.gh.bot.email;

import com.google.api.services.gmail.model.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls Gmail for unread messages, filters out bot auto-replies to prevent loops,
 * and synchronizes valid emails to GitHub.
 */
@ApplicationScoped
public class IncomingMailProcessor {

    private static final Logger LOG = Logger.getLogger(IncomingMailProcessor.class);
    private static final String VISIBLE_MARKER_PREFIX = "**Gmail-Thread-ID:** ";
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("(?m)^--\\s*$|^You received this message because you are subscribed.*");
    private static final String ISSUE_DESCRIPTION_TEMPLATE = "_Thread originally started in the keycloak-security mailing list. Replace the content here by a proper description._";

    @ConfigProperty(name = "google.group.target") String targetGroup;
    @ConfigProperty(name = "gmail.user.email") String botEmail;

    @Inject GmailAdapter gmail;
    @Inject GitHubAdapter github;
    @Inject CommandParser commandParser;

    public void processUnreadEmails() {
        String query = "is:unread -from:" + botEmail;

        List<Message> messages = gmail.fetchUnreadMessages(query);
        for (Message msgSummary : messages) {
            processMessage(msgSummary);
        }
    }

    private void processMessage(Message msgSummary) {
        Message msg = gmail.getMessage(msgSummary.getId());
        if (msg == null) return;

        try {
            String from = gmail.getHeader(msg, "From");

            if (isFromBot(from) || !isValidGroupMessage(msg)) {
                gmail.markAsRead(msg.getId());
                return;
            }

            String threadId = msg.getThreadId();
            String subject = gmail.getHeader(msg, "Subject");
            String cleanBody = sanitizeBody(gmail.getBody(msg)).orElse("(No content)");

            github.findIssueByThreadId(threadId).ifPresentOrElse(
                    existing -> appendComment(existing, from, cleanBody, threadId),
                    () -> createNewIssue(threadId, subject, from, cleanBody)
            );

            gmail.markAsRead(msg.getId());

        } catch (IOException e) {
            LOG.warnf("Deferred processing message %s due to API error (Rate Limit?): %s", msg.getId(), e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process message %s", msg.getId());
        }
    }

    private void appendComment(GHIssue issue, String from, String body, String threadId) {
        String comment = "**Reply from " + from + ":**\n\n" + body;
        github.commentOnIssue(issue, comment);
        LOG.debugf("✅ Commented on #%d (Thread %s)", issue.getNumber(), threadId);
    }

    private void createNewIssue(String threadId, String subject, String from, String body) {
        LOG.debugf("🤖 Creating Issue for Thread %s", threadId);
        GHIssue newIssue = github.createIssue(subject, ISSUE_DESCRIPTION_TEMPLATE);
        if (newIssue != null) {
            try {
                newIssue.addLabels(Labels.STATUS_TRIAGE);
            } catch (Exception e) {
                LOG.errorf(e, "Failed to label issue #%d", newIssue.getNumber());
            }
            String firstComment = VISIBLE_MARKER_PREFIX + threadId + "\n**From:** " + from + "\n\n" + body;
            github.commentOnIssue(newIssue, firstComment);
        }
    }

    private boolean isFromBot(String from) {
        return from != null && from.toLowerCase().contains(botEmail.toLowerCase());
    }

    private boolean isValidGroupMessage(Message msg) {
        String listId = gmail.getHeader(msg, "List-ID");
        String groupIdentifier = targetGroup.split("@")[0];
        if (listId != null && listId.contains(groupIdentifier)) return true;

        String to = gmail.getHeader(msg, "To");
        String cc = gmail.getHeader(msg, "Cc");
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