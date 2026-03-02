package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;

@Command(name = "secalert", description = "Handles security alerts and multiline replies")
public class SecAlertCommand extends CommandParser implements BotCommand { //Required due to a bug on Airline

    private static final Logger LOGGER = Logger.getLogger(SecAlertCommand.class);

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        ParsedMessage msg = extractMessage(payload);
        if (msg == null) return;

        if (msg.commandLine().equals("@security secalert")) {
            LOGGER.infof("New Message to secalert:\n%s", msg.body());
            success(payload);
        } else {
            fail(payload, "Invalid command. Extra text found on the command line.");
        }
    }
}