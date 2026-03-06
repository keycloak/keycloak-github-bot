package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Arguments;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CommandParser implements BotCommand {

    private static final Logger LOGGER = Logger.getLogger(CommandParser.class);
    private static final Set<String> ACTIVE_COMMANDS = ConcurrentHashMap.newKeySet();

    // Absorbs all invalid trailing text to prevent parser crashes
    @Arguments
    protected List<String> unparsedArgs = new ArrayList<>();

    @Override
    public final void run(GHEventPayload.IssueComment issueCommentPayload) throws IOException {
        if (!isAuthorizedRepository(issueCommentPayload)) {
            return;
        }

        String commandKey = buildCommandKey(issueCommentPayload);
        if (!ACTIVE_COMMANDS.add(commandKey)) {
            LOGGER.infof("Duplicate webhook detected for %s. Skipping.", commandKey);
            return;
        }

        try {
            execute(issueCommentPayload);
        } finally {
            ACTIVE_COMMANDS.remove(commandKey);
        }
    }

    private String buildCommandKey(GHEventPayload.IssueComment payload) {
        return getClass().getSimpleName()
                + ":" + payload.getComment().getNodeId();
    }

    private boolean isAuthorizedRepository(GHEventPayload.IssueComment payload) {
        String allowedRepository = ConfigProvider.getConfig()
                .getOptionalValue("repository.privateRepository", String.class)
                .orElse(null);

        String currentRepository = payload.getRepository().getFullName();

        if (allowedRepository == null || !allowedRepository.equalsIgnoreCase(currentRepository)) {
            LOGGER.errorf("Execution aborted: Unauthorized repository [%s]. Expected [%s].",
                    currentRepository, allowedRepository);
            return false;
        }
        return true;
    }

    protected abstract void execute(GHEventPayload.IssueComment payload) throws IOException;

    protected void success(GHEventPayload.IssueComment payload) throws IOException {
        payload.getComment().createReaction(ReactionContent.PLUS_ONE);
    }

    protected void fail(GHEventPayload.IssueComment payload, String reason) throws IOException {
        LOGGER.errorf("Execution aborted: %s", reason);
        payload.getComment().createReaction(ReactionContent.MINUS_ONE);
    }

    /**
     * Ensure that a body exists and is placed on a new line.
     */
    protected Optional<ParsedMessage> extractMessage(GHEventPayload.IssueComment payload) throws IOException {
        String commentBody = payload.getComment().getBody();
        if (commentBody == null || commentBody.isBlank()) {
            fail(payload, "Empty comment body.");
            return Optional.empty();
        }

        int newlineIndex = commentBody.indexOf('\n');
        if (newlineIndex == -1) {
            fail(payload, "Invalid formatting. The message body MUST be placed on a new line below the command.");
            return Optional.empty();
        }

        String rawCommand = commentBody.substring(0, newlineIndex);
        String commandLine = rawCommand.trim().replaceAll("\\s+", " ");
        String body = commentBody.substring(newlineIndex + 1).trim();

        if (body.isEmpty()) {
            fail(payload, "Empty message body provided.");
            return Optional.empty();
        }

        return Optional.of(new ParsedMessage(commandLine, body));
    }

    protected Optional<String> findThreadId(GHEventPayload.IssueComment payload, Pattern pattern) throws IOException {
        for (GHIssueComment comment : payload.getIssue().queryComments().list()) {
            String body = comment.getBody();
            if (body == null) continue;

            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    protected record ParsedMessage(String commandLine, String body) {
    }
}