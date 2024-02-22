package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import io.quarkiverse.githubapp.event.IssueComment;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Status;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class BugActionRetriageMissingInfo {

    private static final Logger logger = Logger.getLogger(BugActionRetriageMissingInfo.class);

    void onEdit(@Issue.Edited GHEventPayload.Issue payload) throws IOException {
        check(payload.getIssue(), payload.getSender());
    }

    void onComment(@IssueComment.Created GHEventPayload.IssueComment payload) throws IOException {
        check(payload.getIssue(), payload.getSender());
    }

    void check(GHIssue issue, GHUser sender) throws IOException {
        if (issue.getState().equals(GHIssueState.OPEN) && sender.getLogin().equals(issue.getUser().getLogin())) {
            Set<String> labels = issue.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());
            if (labels.contains(Status.MISSING_INFORMATION.toLabel())) {
                issue.addLabels(Status.TRIAGE.toLabel());
                issue.removeLabels(Status.MISSING_INFORMATION.toLabel(), Status.AUTO_EXPIRE.toLabel());
                logger.infov("Moving back to triage: issue={0}", issue.getNumber());
            }
        }
    }

}
