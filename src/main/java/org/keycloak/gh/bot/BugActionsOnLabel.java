package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import jakarta.inject.Inject;
import org.keycloak.gh.bot.labels.Action;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;

public class BugActionsOnLabel {

    @Inject
    BugActions bugActions;

    void onLabeled(@Issue.Labeled GHEventPayload.Issue payload) throws IOException {
        String label = payload.getLabel().getName();
        GHIssue issue = payload.getIssue();

        if (Action.isInstance(label)) {
            Action action = Action.fromLabel(label);
            bugActions.runAction(action, issue);
        }
    }

}