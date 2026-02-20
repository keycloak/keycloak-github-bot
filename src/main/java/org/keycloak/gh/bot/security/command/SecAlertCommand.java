package org.keycloak.gh.bot.security.command;

import com.github.rvesse.airline.annotations.Arguments;
import com.github.rvesse.airline.annotations.Command;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;

@Command(name = "secalert", description = "Handles security alerts and multiline replies")
public class SecAlertCommand extends CommandParser implements BotCommand { //Required due to a bug on Airline

    private static final Logger LOGGER = Logger.getLogger(SecAlertCommand.class);

    @Override
    protected void execute(GHEventPayload.IssueComment payload) throws IOException {
        if (!unparsedArgs.isEmpty() && unparsedArgs.get(0).equalsIgnoreCase("reply")) {
            handleReply(payload);
        } else {
            handleNewMessage(payload);
        }
    }

    private void handleNewMessage(GHEventPayload.IssueComment payload) throws IOException {
        String rawBody = payload.getComment().getBody();
        String message = rawBody.replaceFirst("(?i)^@security\\s+secalert\\s*", "").trim();

        LOGGER.infof("New Message to secalert:\n%s", message);

        payload.getComment().createReaction(ReactionContent.PLUS_ONE);
    }

    private void handleReply(GHEventPayload.IssueComment payload) throws IOException {
        String rawBody = payload.getComment().getBody();
        String message = rawBody.replaceFirst("(?i)^@security\\s+secalert\\s+reply\\s*", "").trim();

        LOGGER.infof("Reply Message to secalert:\n%s", message);

        payload.getComment().createReaction(ReactionContent.PLUS_ONE);
    }
}