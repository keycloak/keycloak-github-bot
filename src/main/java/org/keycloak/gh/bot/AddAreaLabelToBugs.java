package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.utils.IssueParser;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;

/**
 * On issue open, detects issue template structure from the body and applies appropriate labels.
 * For bugs: adds kind/bug, status/triage, and area label.
 * For enhancement/feature: adds status/triage (template structure is identical for both).
 */
public class AddAreaLabelToBugs {

    void onOpen(@Issue.Opened GHEventPayload.Issue issuePayload) throws IOException {
        GHIssue issue = issuePayload.getIssue();
        String body = issue.getBody();

        boolean isBug = Labels.hasLabel(issue, Labels.KIND_BUG);

        if (!isBug && !Labels.hasAnyLabelWithPrefix(issue, "kind/")) {
            // When an issue is created by a user's AI using the API, it will not have a type or labels set
            // Derive the necessary information from the template used.
            IssueParser.TemplateType template = IssueParser.detectTemplateType(body);
            if (template == IssueParser.TemplateType.BUG) {
                Labels.addLabelIfExists(issue, Labels.KIND_BUG);
                Labels.addLabelIfExists(issue, Labels.STATUS_TRIAGE);
                isBug = true;
            } else if (template == IssueParser.TemplateType.ENHANCEMENT_OR_FEATURE) {
                // Assume it is an enhancement, as we can't distinguish enhancement from feature
                // The triager might then change this to a feature later.
                Labels.addLabelIfExists(issue, Labels.KIND_ENHANCEMENT);
                Labels.addLabelIfExists(issue, Labels.STATUS_TRIAGE);
            }
        }

        if (isBug) {
            String areaLabel = IssueParser.getAreaFromBody(body);
            if (areaLabel != null) {
                Labels.addLabelIfExists(issue, areaLabel);
            }
        }
    }

}
