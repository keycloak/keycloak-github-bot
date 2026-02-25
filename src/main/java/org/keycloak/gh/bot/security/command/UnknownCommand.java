package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;

// Required if we want to override the default behavior of Airline. By default errors will be
// displayed as a response on GitHub
@Command(name = "unknown", description = "Swallows unknown commands safely", hidden = true)
public class UnknownCommand extends CommandParser implements BotCommand { //Required due to a bug on Airline

    private static final Logger LOG = Logger.getLogger(UnknownCommand.class);

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        LOG.errorf("Execution aborted: Unrecognized command. Context: %s", unparsedArgs);
        payload.getComment().createReaction(org.kohsuke.github.ReactionContent.MINUS_ONE);
    }
}