package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.BackportLabels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.Set;

public class LinkBackportPRs {

    private static final Logger logger = Logger.getLogger(LinkBackportPRs.class);

    @ConfigProperty(name = "repository.mainRepository")
    String mainRepository;

    @Inject
    BackportPullRequests pullRequests;

    void onOpen(@PullRequest.Opened GHEventPayload.PullRequest payload) throws IOException {
        link(payload);
    }

    void onEdit(@PullRequest.Edited GHEventPayload.PullRequest payload) throws IOException {
        link(payload);
    }

    void onReopen(@PullRequest.Reopened GHEventPayload.PullRequest payload) throws IOException {
        link(payload);
    }

    private void link(GHEventPayload.PullRequest payload) throws IOException {
        GHRepository repository = payload.getRepository();
        GHPullRequest pr = payload.getPullRequest();
        String branch = pr.getBase().getRef();
        if (!repository.getFullName().equalsIgnoreCase(mainRepository) || pr.getState() != GHIssueState.OPEN
                || !branch.startsWith("release/")) {
            return;
        }
        Set<Integer> issueNumbers = BackportPullRequests.closingIssues(pr.getBody(), repository.getFullName());
        if (issueNumbers.isEmpty()) {
            return;
        }
        Set<String> repositoryBranches = repository.getBranches().keySet();
        if (repositoryBranches.contains("archive/" + branch)) {
            return;
        }
        for (int issueNumber : issueNumbers) {
            GHIssue issue;
            try {
                issue = repository.getIssue(issueNumber);
            } catch (GHFileNotFoundException e) {
                logger.warnv("Referenced issue not found: repository={0}, issue={1}", repository.getFullName(), issueNumber);
                continue;
            }
            if (!issue.isPullRequest() && !issue.isLocked()
                    && issue.getState() == GHIssueState.CLOSED && issue.getStateReason() == GHIssueStateReason.COMPLETED
                    && BackportLabels.requiredBranches(issue.getLabels(), repositoryBranches).contains(branch)) {
                pullRequests.link(issue, pr);
            }
        }
    }
}
