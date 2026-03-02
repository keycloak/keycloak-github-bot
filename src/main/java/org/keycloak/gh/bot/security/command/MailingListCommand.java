package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Command;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;

@Command(name = "reply", description = "Reply to e-mails received from the keycloak-security mailing list and the sender")
public class MailingListCommand extends CommandParser implements BotCommand { //Required due to a bug on Airline

    private static final Logger LOGGER = Logger.getLogger(MailingListCommand.class);

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        LOGGER.infof("Replying to the keycloak security: %s", payload.getRepository().getFullName());
        ParsedMessage msg = extractMessage(payload);
        if (msg == null) return;

        if (!msg.commandLine().equals("@security reply")) {
            fail(payload, "Invalid command signature. Extra text found on the command line.");
            return;
        }

        success(payload);
    }
}