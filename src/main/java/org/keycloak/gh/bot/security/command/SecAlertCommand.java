package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.email.MailSender;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sends emails to SecAlert. Targets secalert.email.to for the initial outreach and secalert.email.reply-to once a
 * SecAlert-Thread-ID (captured from the reply address) is recorded on the issue.
 */
@Command(name = "secalert", description = "Sends a new email or reply to SecAlert and tracks the reply thread ID")
public class SecAlertCommand extends CommandParser implements BotCommand {

    private static final Logger LOGGER = Logger.getLogger(SecAlertCommand.class);
    private static final Pattern SECALERT_THREAD_ID_PATTERN = Pattern.compile(
            Pattern.quote(Constants.SECALERT_THREAD_ID_PREFIX) + "\\s*([a-f0-9]+)");

    @Inject
    MailSender mailSender;

    @ConfigProperty(name = "secalert.email.to")
    String secAlertTo;

    @ConfigProperty(name = "secalert.email.reply-to")
    String secAlertReplyTo;

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        Optional<ParsedMessage> msg = extractMessage(payload);
        if (msg.isEmpty()) return;

        String subject = String.join(" ", unparsedArgs);
        String body = msg.get().body();

        Optional<String> existingThreadId = findThreadId(payload, SECALERT_THREAD_ID_PATTERN);

        if (existingThreadId.isPresent()) {
            replyToExistingThread(payload, existingThreadId.get(), body);
        } else {
            createNewThread(payload, subject, body);
        }
    }

    private void createNewThread(GHEventPayload.IssueComment payload, String subject, String body) throws IOException {
        if (subject.isEmpty()) {
            fail(payload, "Subject is required for the first SecAlert email. Usage: @security secalert <subject>");
            return;
        }

        GHIssue issue = payload.getIssue();
        String taggedSubject = subject + " - " + Constants.GHI_ISSUE_PREFIX + issue.getNumber();

        LOGGER.infof("Sending new SecAlert email for issue #%d, subject: %s", issue.getNumber(), taggedSubject);

        Optional<String> threadId = mailSender.sendNewEmail(secAlertTo, targetGroup, taggedSubject, body);
        if (threadId.isEmpty()) {
            fail(payload, "Failed to send email to SecAlert via Gmail API.");
            return;
        }

        Set<String> currentLabels = issue.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
        if (currentLabels.contains(Status.TRIAGE.toLabel())) {
            issue.removeLabels(Status.TRIAGE.toLabel());
        }
        issue.addLabels(Status.CVE_REQUEST.toLabel());

        String title = issue.getTitle();
        if (title != null && !title.startsWith(Constants.CVE_TBD_PREFIX)) {
            issue.setTitle(Constants.CVE_TBD_PREFIX + " " + title);
        }
        success(payload);
    }

    private void replyToExistingThread(GHEventPayload.IssueComment payload, String threadId, String body) throws IOException {
        LOGGER.infof("Replying to existing SecAlert thread %s for issue #%d", threadId, payload.getIssue().getNumber());

        if (mailSender.sendThreadedEmail(threadId, secAlertReplyTo, targetGroup, body)) {
            success(payload);
        } else {
            fail(payload, "Failed to send reply to SecAlert thread " + threadId + ".");
        }
    }
}
