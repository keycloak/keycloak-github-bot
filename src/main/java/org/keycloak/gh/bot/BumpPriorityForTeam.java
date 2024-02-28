package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.labels.Label;
import org.keycloak.gh.bot.labels.Priority;
import org.keycloak.gh.bot.labels.Status;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class BumpPriorityForTeam {

    void onOpen(@Issue.Labeled GHEventPayload.Issue payload) throws IOException {
        GHLabel label = payload.getLabel();
        GHIssue issue = payload.getIssue();
        if (label.getName().equals("team/rh-iam")) {
            Priority priority = Priority.IMPORTANT;

            List<String> removeLabels = new LinkedList<>();
            removeLabels.add(Status.AUTO_BUMP.toLabel());
            removeLabels.add(Status.AUTO_EXPIRE.toLabel());
            removeLabels.add(Label.HELP_WANTED.toLabel());

            removeLabels.addAll(payload.getIssue().getLabels().stream().map(GHLabel::getName)
                    .filter(l -> !l.equals(priority.toLabel()))
                    .filter(Priority::isInstance).toList());

            issue.removeLabels(removeLabels.toArray(new String[0]));

            issue.addLabels(Priority.IMPORTANT.toLabel());
        }
    }

}
