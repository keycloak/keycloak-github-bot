package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Arguments;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class CommandParser implements BotCommand {

    private static final Logger LOGGER = Logger.getLogger(CommandParser.class);

    // Absorbs all invalid trailing text to prevent parser crashes
    @Arguments
    protected List<String> unparsedArgs = new ArrayList<>();

    @Override
    public final void run(GHEventPayload.IssueComment issueCommentPayload) throws IOException {
        if (isAuthorizedRepository(issueCommentPayload)) {
            execute(issueCommentPayload);
        }
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
    protected ParsedMessage extractMessage(GHEventPayload.IssueComment payload) throws IOException {
        String[] parts = payload.getComment().getBody().trim().split("[\\r\\n]+", 2);

        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            fail(payload, parts.length < 2
                    ? "Invalid formatting. The message body MUST be placed on a new line below the command."
                    : "Empty message body provided.");
            return null;
        }

        return new ParsedMessage(parts[0].trim().replaceAll("\\s+", " ").toLowerCase(), parts[1].trim());
    }

    protected record ParsedMessage(String commandLine, String body) {
    }
}