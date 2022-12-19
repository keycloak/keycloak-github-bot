package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.utils.IssueParser;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;

/**
 * Adds 'area/...' label to bugs by using the area selected by the reporter in the area dropdown on the bug issue form.
 */
public class AddAreaLabelToBugs {

    void onOpen(@Issue.Opened GHEventPayload.Issue issuePayload) throws IOException {
        GHIssue issue = issuePayload.getIssue();

        if (Labels.hasLabel(issue, Labels.KIND_BUG)) {
            String areaLabel = IssueParser.getAreaFromBody(issuePayload.getIssue().getBody());
            if (areaLabel != null) {
                Labels.addArea(issue, areaLabel);
            }
        }
    }

}
