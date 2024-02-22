package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.labels.Status;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class AddTriageToReopenedBugs {

    void onEdit(@Issue.Reopened GHEventPayload.Issue payload) throws IOException {
        GHIssue issue = payload.getIssue();
        Set<String> labels = payload.getIssue().getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
        if (labels.contains(Kind.BUG.toLabel())) {
            issue.addLabels(Status.TRIAGE.toLabel(), Status.REOPENED.toLabel());
        }
    }

}
