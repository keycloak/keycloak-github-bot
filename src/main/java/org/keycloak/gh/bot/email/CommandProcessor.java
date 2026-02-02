package org.keycloak.gh.bot.email;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHReaction;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the processing of GitHub comments to identify and execute bot commands
 */
@ApplicationScoped
public class CommandProcessor {

    private static final Logger LOG = Logger.getLogger(CommandProcessor.class);
    private static final Pattern VISIBLE_MARKER_PATTERN = Pattern.compile("\\*\\*Gmail-Thread-ID:\\*\\*\\s*([a-f0-9]+)");
    private static final Pattern RAW_HEX_PATTERN = Pattern.compile("\\b([a-f0-9]{16})\\b");

    private static final int MAX_PROCESSED_HISTORY = 10000;

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    @ConfigProperty(name = "email.target.secalert")
    String secAlertEmail;

    @Inject GitHubAdapter github;
    @Inject CommandParser parser;
    @Inject MailSender mailSender;

    private final Set<Long> processedComments = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<Long, Boolean>(MAX_PROCESSED_HISTORY + 1, .75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
                    return size() > MAX_PROCESSED_HISTORY;
                }
            }));

    private Instant lastPollTime = Instant.now().minus(10, ChronoUnit.MINUTES);

    public void processCommands() {
        try {
            String myLogin = parser.getBotName();
            if (myLogin == null || myLogin.isEmpty()) return;

            Instant executionStart = Instant.now();

            Date querySince = Date.from(lastPollTime.minus(1, ChronoUnit.MINUTES));
            List<GHIssue> updatedIssues = github.getIssuesUpdatedSince(querySince);

            for (GHIssue issue : updatedIssues) {
                try {
                    scanIssue(issue, myLogin);
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to scan issue #%d", issue.getNumber());
                }
            }

            lastPollTime = executionStart;
        } catch (Exception e) {
            LOG.error("Fatal error fetching updated issues", e);
        }
    }

    private void scanIssue(GHIssue issue, String myLogin) throws IOException {
        Optional<String> threadIdOpt = findThreadIdInComments(issue);

        List<GHIssueComment> recentComments = issue.queryComments()
                .since(Date.from(lastPollTime.minus(1, ChronoUnit.MINUTES)))
                .list()
                .toList();

        for (GHIssueComment comment : recentComments) {
            if (hasAlreadyProcessed(comment, myLogin)) continue;

            parser.parse(comment.getBody()).ifPresent(cmd -> executeCommand(issue, comment, cmd, threadIdOpt));
        }
    }

    private void executeCommand(GHIssue issue, GHIssueComment comment, CommandParser.Command cmd, Optional<String> threadId) {
        boolean success = false;
        ReactionContent reaction = ReactionContent.EYES;

        switch (cmd.type()) {
            case NEW_SECALERT:
                success = mailSender.sendNewEmail(secAlertEmail, targetGroup, cmd.subject().orElse("No Subject"), cmd.body());
                if (!success) {
                    replyWithError(issue, comment, "❌ Error: Failed to send email via Gmail API.");
                    reaction = ReactionContent.CONFUSED;
                }
                break;
            case REPLY_KEYCLOAK_SECURITY:
                if (threadId.isPresent()) {
                    success = mailSender.sendReply(threadId.get(), issue.getTitle(), cmd.body(), targetGroup);
                    if (!success) {
                        replyWithError(issue, comment, "❌ Error: Failed to send email via Gmail API.");
                        reaction = ReactionContent.CONFUSED;
                    }
                } else {
                    replyWithError(issue, comment, "❌ Error: Gmail Thread ID not found.");
                    success = true;
                    reaction = ReactionContent.CONFUSED;
                }
                break;
            case UNKNOWN:
                sendHelpMessage(issue, comment);
                success = true;
                reaction = ReactionContent.CONFUSED;
                break;
        }

        if (success) {
            processedComments.add(comment.getId());
            addReaction(comment, reaction);
            LOG.debugf("✅ Command executed: %s", cmd.type());
        }
    }

    private void addReaction(GHIssueComment comment, ReactionContent reaction) {
        try {
            comment.createReaction(reaction);
        } catch (IOException e) {
            LOG.errorf("Failed to react to comment %d", comment.getId());
        }
    }

    private void replyWithError(GHIssue issue, GHIssueComment comment, String message) {
        try {
            github.commentOnIssue(issue, "@" + comment.getUser().getLogin() + " " + message);
        } catch (IOException e) {
            LOG.error("Failed to send error reply", e);
        }
    }

    private void sendHelpMessage(GHIssue issue, GHIssueComment comment) {
        try {
            String body = "@" + comment.getUser().getLogin() + " " + parser.getHelpMessage();
            github.commentOnIssue(issue, body);
        } catch (IOException e) {
            LOG.error("Failed to send help message", e);
        }
    }

    private boolean hasAlreadyProcessed(GHIssueComment comment, String myLogin) throws IOException {
        if (processedComments.contains(comment.getId())) return true;

        for (GHReaction reaction : comment.listReactions()) {
            String user = reaction.getUser().getLogin();
            if ((reaction.getContent() == ReactionContent.EYES || reaction.getContent() == ReactionContent.CONFUSED) &&
                    (user.equalsIgnoreCase(myLogin) || user.equalsIgnoreCase(myLogin + "[bot]"))) {
                processedComments.add(comment.getId());
                return true;
            }
        }
        return false;
    }

    private Optional<String> findThreadIdInComments(GHIssue issue) throws IOException {
        for (GHIssueComment comment : issue.getComments()) {
            Matcher m = VISIBLE_MARKER_PATTERN.matcher(comment.getBody());
            if (m.find()) return Optional.of(m.group(1).trim());
            Matcher raw = RAW_HEX_PATTERN.matcher(comment.getBody());
            if (raw.find()) return Optional.of(raw.group(1).trim());
        }
        return Optional.empty();
    }
}