package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.utils.IssueParser;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds 'area/...' label to bugs by using the area selected by the reporter in the area dropdown on the bug issue form.
 */
public class AddAreaLabelToBugs {
    private static final String TEAM_UI_LABEL = "team/ui";
    private static final Set<String> TEAM_UI_AREAS =
        Set.of("account/ui", "adapter/javascript", "admin/ui", "admin/client")
           .stream()
           .map(area -> IssueParser.AREA_PREFIX.concat(area))
           .collect(Collectors.toSet());

    void onOpen(@Issue.Opened GHEventPayload.Issue issuePayload) throws IOException {
        GHIssue issue = issuePayload.getIssue();

        if (Labels.hasLabel(issue, Labels.KIND_BUG)) {
            String areaLabel = IssueParser.getAreaFromBody(issuePayload.getIssue().getBody());
            if (areaLabel != null) {
                Labels.addLabelIfExists(issue, areaLabel);
            }

            if (TEAM_UI_AREAS.contains(areaLabel)) {
                Labels.addLabelIfExists(issue, TEAM_UI_LABEL);
            }
        }
    }

}
