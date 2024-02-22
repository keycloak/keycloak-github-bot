package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.IssueComment;
import jakarta.inject.Inject;
import org.keycloak.gh.bot.labels.Action;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPermissionType;

import java.io.IOException;

public class BugActionsOnComment {

    @Inject
    BugActions bugActions;

    void onLabeled(@IssueComment.Created GHEventPayload.IssueComment payload) throws IOException {
        if (Labels.hasLabel(payload.getIssue(), Kind.BUG.toLabel())) {
            Action action = getAction(payload.getComment().getBody());

            GHPermissionType permission = payload.getRepository().getPermission(payload.getSender());
            if (GHPermissionType.WRITE.equals(permission) || GHPermissionType.ADMIN.equals(permission)) {
                bugActions.runAction(action, payload.getIssue());
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