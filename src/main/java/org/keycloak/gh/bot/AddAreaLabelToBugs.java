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
 * For enhancement and feature: adds the kind label and status/triage.
 * For tasks: adds kind/task.
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
            if (template != null) {
                switch (template) {
                    case BUG -> {
                        Labels.addLabelIfExists(issue, Labels.KIND_BUG);
                        Labels.addLabelIfExists(issue, Labels.STATUS_TRIAGE);
                        isBug = true;
                    }
                    case ENHANCEMENT -> {
                        Labels.addLabelIfExists(issue, Labels.KIND_ENHANCEMENT);
                        Labels.addLabelIfExists(issue, Labels.STATUS_TRIAGE);
                    }
                    case FEATURE -> {
                        Labels.addLabelIfExists(issue, Labels.KIND_FEATURE);
                        Labels.addLabelIfExists(issue, Labels.STATUS_TRIAGE);
                    }
                    case TASK -> Labels.addLabelIfExists(issue, Labels.KIND_TASK);
                }
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
