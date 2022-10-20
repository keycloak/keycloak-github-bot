package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;

/**
 * Removes the 'status/triage' when an issue is closed.
 */
public class RemoveTriageLabelOnClose {

    void onClose(@Issue.Closed GHEventPayload.Issue issuePayload) throws IOException {
        GHIssue issue = issuePayload.getIssue();
        Labels.removeLabel(issue, Labels.STATUS_TRIAGE);
    }

}
