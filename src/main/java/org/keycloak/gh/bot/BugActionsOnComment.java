package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.IssueComment;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Action;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHUser;

import java.io.IOException;

public class BugActionsOnComment {

    private static final Logger logger = Logger.getLogger(BugActionsOnComment.class);

    @Inject
    BugActions bugActions;

    void onCommentCreated(@IssueComment.Created GHEventPayload.IssueComment payload) throws IOException {
        if (Labels.hasLabel(payload.getIssue(), Kind.BUG.toLabel())) {
            Action action = getAction(payload.getComment().getBody());
            if (action != null) {
                GHUser sender = payload.getSender();
                if (sender.getType().equals("User") && sender.isMemberOf(payload.getOrganization())) {
                    bugActions.runAction(action, payload.getIssue());
                }
            }
        }
    }

    Action getAction(String body) {
        int lastNewLine = body.lastIndexOf("\n");
        if (lastNewLine > 0) {
            body = body.substring(lastNewLine).trim();
        } else {
            body = body.trim();
        }

        if (body.startsWith("~") && Action.isInstance(body.replace("~", "action/"))) {
            return Action.fromAction(body.substring(1));
        }

        return null;
    }

}