package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.labels.Priority;
import org.keycloak.gh.bot.labels.Status;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class UpdatedLabelsOnPriorityChange {

    void onOpen(@Issue.Labeled GHEventPayload.Issue payload) throws IOException {
        GHLabel label = payload.getLabel();
        GHIssue issue = payload.getIssue();
        if (Priority.isInstance(label.getName())) {
            Priority priority = Priority.fromLabel(label.getName());
            if (priority.equals(Priority.IMPORTANT) || priority.equals(Priority.BLOCKER)) {
                List<String> removeLabels = new LinkedList<>();
                removeLabels.add(Status.AUTO_BUMP.toLabel());
                removeLabels.add(Status.AUTO_EXPIRE.toLabel());

                removeLabels.addAll(payload.getIssue().getLabels().stream().map(GHLabel::getName)
                        .filter(l -> !l.equals(label.getName()))
                        .filter(Priority::isInstance).toList());

                issue.removeLabels(removeLabels.toArray(new String[0]));
            }
        }
    }

}
