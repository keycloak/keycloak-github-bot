package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHTeam;

import java.io.IOException;

public class AddTeamLabelToPRs {

    private String MAINTAINERS_TEAM_SUFFIX = "-maintainers";

    void onOpen(@PullRequest.ReviewRequested GHEventPayload.PullRequest pullRequest) throws IOException {
        for (GHTeam team : pullRequest.getPullRequest().getRequestedTeams()) {
            String teamName = team.getName();
            if (teamName.endsWith(MAINTAINERS_TEAM_SUFFIX)) {
                teamName = teamName.substring(0, teamName.length() - MAINTAINERS_TEAM_SUFFIX.length());
            }

            if (!teamName.equals("maintainers")) {
                Labels.addLabelIfExists(pullRequest.getPullRequest(), "team/" + teamName);
            }
        }

    }

}
