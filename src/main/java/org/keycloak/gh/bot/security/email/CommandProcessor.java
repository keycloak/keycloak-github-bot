package org.keycloak.gh.bot.security.email;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.security.common.CommandParser;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.common.GitHubAdapter;
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

@ApplicationScoped
public class CommandProcessor {

    private static final Logger LOG = Logger.getLogger(CommandProcessor.class);

    private static final Pattern REPORTER_THREAD_ID_PATTERN = Pattern.compile(Pattern.quote(Constants.GMAIL_THREAD_ID_PREFIX) + "\\s*([a-f0-9]+)");
    private static final Pattern SECALERT_THREAD_ID_PATTERN = Pattern.compile(Pattern.quote(Constants.SECALERT_THREAD_ID_PREFIX) + "\\s*([a-f0-9]+)");

    @ConfigProperty(name = "google.group.target") String targetGroup;
    @ConfigProperty(name = "email.sender.secalert") String secAlertEmail;

    @Inject GitHubAdapter github;
    @Inject CommandParser parser;
    @Inject MailSender mailSender;

    private final Set<Long> processedComments = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<Long, Boolean>(10000 + 1, .75F, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) { return size() > 10000; }
            }));

    private Instant lastPollTime = Instant.now().minus(10, ChronoUnit.MINUTES);

    public void processCommands() {
        if (github.isAccessDenied()) return;
        try {
            Instant executionStart = Instant.now();
            List<GHIssue> updatedIssues = github.getIssuesUpdatedSince(Date.from(lastPollTime.minus(1, ChronoUnit.MINUTES)));

            for (GHIssue issue : updatedIssues) {
                scanIssue(issue);
            }
            lastPollTime = executionStart;
        } catch (Exception e) {
            LOG.error("Fatal error fetching updated issues", e);
        }
    }

    private void scanIssue(GHIssue issue) {
        try {
            Instant threshold = lastPollTime.minus(1, ChronoUnit.MINUTES);

            List<GHIssueComment> recentComments = issue.queryComments()
                    .since(Date.from(threshold))
                    .list()
                    .toList();

            if (recentComments.isEmpty()) {
                return;
            }

            List<GHIssueComment> commandComments = recentComments.stream()
                    .filter(c -> !processedComments.contains(c.getId()))
                    .filter(c -> parser.parse(c.getBody()).isPresent())
                    .toList();

            if (commandComments.isEmpty()) {
                return;
            }

            List<GHIssueComment> allComments = issue.queryComments().list().toList();
            ThreadIds threadIds = findThreadIds(allComments);

            for (GHIssueComment comment : commandComments) {
                if (processedComments.contains(comment.getId())) continue;

                parser.parse(comment.getBody()).ifPresent(cmd -> {
                    try {
                        if (hasAlreadyProcessed(comment)) return;
                        executeCommand(issue, comment, cmd, threadIds);
                    } catch (IOException e) {
                        LOG.errorf(e, "Error on comment %d", comment.getId());
                    }
                });
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to scan issue #%d", issue.getNumber());
        }
    }

    private record ThreadIds(Optional<String> reporter, Optional<String> secAlert) {}

    private ThreadIds findThreadIds(List<GHIssueComment> comments) {
        String reporter = null;
        String secAlert = null;

        for (GHIssueComment comment : comments) {
            String body = comment.getBody();
            if (body == null) continue;

            if (reporter == null) {
                Matcher m = REPORTER_THREAD_ID_PATTERN.matcher(body);
                if (m.find()) reporter = m.group(1);
            }
            if (secAlert == null) {
                Matcher m = SECALERT_THREAD_ID_PATTERN.matcher(body);
                if (m.find()) secAlert = m.group(1);
            }

            if (reporter != null && secAlert != null) break;
        }
        return new ThreadIds(Optional.ofNullable(reporter), Optional.ofNullable(secAlert));
    }

    private void executeCommand(GHIssue issue, GHIssueComment comment, CommandParser.Command cmd, ThreadIds threadIds) throws IOException {
        boolean success = false;
        ReactionContent reaction = ReactionContent.EYES;

        switch (cmd.type()) {
            case NEW_SECALERT -> {
                String rawSubject = cmd.subject().orElse("No Subject");
                String generatedThreadId = mailSender.sendNewEmail(secAlertEmail, targetGroup, rawSubject, cmd.body());

                success = generatedThreadId != null;
                if (success) {
                    String marker = Constants.SECALERT_THREAD_ID_PREFIX + " " + generatedThreadId;
                    github.commentOnIssue(issue, "✅ SecAlert email sent. " + marker);

                    String prefixedTitle = Constants.CVE_TBD_PREFIX + " " + rawSubject;
                    github.updateTitleAndLabels(issue, prefixedTitle, null);
                }
            }
            case REPLY_KEYCLOAK_SECURITY -> {
                if (threadIds.reporter().isPresent()) {
                    success = mailSender.sendReply(threadIds.reporter().get(), cmd.body(), targetGroup);
                } else {
                    replyWithError(issue, comment, "❌ Error: Reporter Thread ID not found (cannot reply to keycloak-security).");
                    success = true;
                    reaction = ReactionContent.CONFUSED;
                }
            }
            case REPLY_SECALERT -> {
                if (threadIds.secAlert().isPresent()) {
                    success = mailSender.sendThreadedEmail(threadIds.secAlert().get(), secAlertEmail, targetGroup, cmd.body());
                } else {
                    replyWithError(issue, comment, "❌ Error: SecAlert Thread ID not found. Did you start a thread with /new secalert?");
                    success = true;
                    reaction = ReactionContent.CONFUSED;
                }
            }
            case UNKNOWN -> {
                sendHelpMessage(issue, comment);
                success = true;
                reaction = ReactionContent.CONFUSED;
            }
        }

        if (success) {
            processedComments.add(comment.getId());
            addReaction(comment, reaction);
        } else {
            replyWithError(issue, comment, "❌ Error: Failed to execute command via Gmail API.");
            addReaction(comment, ReactionContent.CONFUSED);
        }
    }

    private void addReaction(GHIssueComment comment, ReactionContent reaction) {
        try { comment.createReaction(reaction); } catch (IOException e) { LOG.error("Failed to react", e); }
    }

    private void replyWithError(GHIssue issue, GHIssueComment comment, String message) {
        try { github.commentOnIssue(issue, "@" + comment.getUser().getLogin() + " " + message); } catch (IOException e) { LOG.error("Failed reply", e); }
    }

    private void sendHelpMessage(GHIssue issue, GHIssueComment comment) {
        try { github.commentOnIssue(issue, "@" + comment.getUser().getLogin() + "\n" + parser.getHelpMessage()); } catch (IOException e) { LOG.error("Failed help", e); }
    }

    private boolean hasAlreadyProcessed(GHIssueComment comment) throws IOException {
        String botLogin = parser.getBotName();
        for (GHReaction reaction : comment.listReactions()) {
            String user = reaction.getUser().getLogin();
            if ((reaction.getContent() == ReactionContent.EYES || reaction.getContent() == ReactionContent.CONFUSED) &&
                    (user.equalsIgnoreCase(botLogin) || user.equalsIgnoreCase(botLogin + "[bot]"))) {
                processedComments.add(comment.getId());
                return true;
            }
        }
        return false;
    }
}