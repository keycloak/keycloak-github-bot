package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;

// Required if we want to override the default behavior of Airline. By default errors will be
// displayed as a response on GitHub
@Command(name = "unknown", description = "Swallows unknown commands safely", hidden = true)
public class UnknownCommand extends CommandParser implements BotCommand { //Required due to a bug on Airline

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        fail(payload, "Unrecognized command. Context: " + unparsedArgs);
    }
}