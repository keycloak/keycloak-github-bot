package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.email.MailSender;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rejects a security report by notifying the reporter via email, applying the rejected label, and closing the issue.
 */
@Command(name = "reject", description = "Rejects a security report, notifies the reporter, and closes the issue")
public class RejectCommand extends CommandParser implements BotCommand {

    private static final Logger LOGGER = Logger.getLogger(RejectCommand.class);

    @Inject
    MailSender mailSender;

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        Optional<ParsedMessage> msg = extractMessage(payload);
        if (msg.isEmpty()) return;

        GHIssue issue = payload.getIssue();
        String reasoning = msg.get().body();

        if (Labels.hasLabel(issue, Labels.SOURCE_EMAIL)) {
            Optional<String> threadId = findThreadId(payload, Constants.GMAIL_THREAD_ID_PATTERN);
            if (threadId.isEmpty()) {
                fail(payload, "Gmail Thread ID not found in issue comments. Cannot reject without notifying the reporter.");
                return;
            }

            LOGGER.infof("Sending rejection reply to Gmail thread %s for issue #%d", threadId.get(), issue.getNumber());

            if (!mailSender.sendReply(threadId.get(), reasoning, targetGroup)) {
                fail(payload, "Failed to send rejection reply via Gmail API.");
                return;
            }
        } else {
            LOGGER.infof("Issue #%d has no %s label. Skipping email notification.", issue.getNumber(), Labels.SOURCE_EMAIL);
        }

        Set<String> currentLabels = issue.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());

        issue.addLabels(Status.REJECTED.toLabel());

        if (currentLabels.contains(Status.TRIAGE.toLabel())) {
            issue.removeLabels(Status.TRIAGE.toLabel());
        }

        issue.close(GHIssueStateReason.NOT_PLANNED);
        success(payload);
    }
}
