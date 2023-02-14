package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.representations.Teams;
import org.kohsuke.github.GHEventPayload;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AddTeamLabelToIssues {

    void onOpen(@Issue.Labeled GHEventPayload.Issue issuePayload) throws IOException {
        Teams teams = Teams.getTeams();
        String labelName = issuePayload.getLabel().getName();
        if (labelName.startsWith("area/")) {
            for (Map.Entry<String, List<String>> e : teams.entrySet()) {
                if (e.getValue().contains(labelName)) {
                    issuePayload.getIssue().addLabels(e.getKey());
                }
            }
        }
    }

}
