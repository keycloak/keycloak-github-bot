package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.email.MailSender;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueComment;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replies to the keycloak-security mailing list and the original sender when invoked via @security reply.
 */
@Command(name = "reply", description = "Reply to e-mails received from the keycloak-security mailing list and the sender")
public class MailingListCommand extends CommandParser implements BotCommand {

    private static final Logger LOGGER = Logger.getLogger(MailingListCommand.class);
    private static final Pattern THREAD_ID_PATTERN = Pattern.compile(
            Pattern.quote(Constants.GMAIL_THREAD_ID_PREFIX) + "\\s*([a-f0-9]+)");

    @Inject
    MailSender mailSender;

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        ParsedMessage msg = extractMessage(payload);
        if (msg == null) return;

        if (!msg.commandLine().equals("@security reply")) {
            fail(payload, "Invalid command signature. Extra text found on the command line.");
            return;
        }

        Optional<String> threadId = findGmailThreadId(payload);
        if (threadId.isEmpty()) {
            fail(payload, "Gmail Thread ID not found in issue comments. Cannot reply without a linked email thread.");
            return;
        }

        LOGGER.infof("Sending reply to Gmail thread %s for issue #%d", threadId.get(), payload.getIssue().getNumber());

        if (mailSender.sendReply(threadId.get(), msg.body(), targetGroup)) {
            success(payload);
        } else {
            fail(payload, "Failed to send reply via Gmail API.");
        }
    }

    private Optional<String> findGmailThreadId(GHEventPayload.IssueComment payload) throws IOException {
        for (GHIssueComment comment : payload.getIssue().queryComments().list()) {
            String body = comment.getBody();
            if (body == null) continue;

            Matcher matcher = THREAD_ID_PATTERN.matcher(body);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
