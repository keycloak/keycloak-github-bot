package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Arguments;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;

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
}