package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.PullRequestParser;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestChanges;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class LinkBackportPRs {

    private static final Logger logger = Logger.getLogger(LinkBackportPRs.class);

    private static final String LINK_PR = """
            mutation($issueId: ID!, $pullRequestIds: [ID!]!) {
                addCloseIssueReferences(input: {issueId: $issueId, pullRequestIds: $pullRequestIds}) {
                    issue { id }
                }
            }
            """;

    private static final String UNLINK_PR = """
            mutation($issueId: ID!, $pullRequestIds: [ID!]!) {
                removeCloseIssueReferences(input: {issueId: $issueId, pullRequestIds: $pullRequestIds}) {
                    issue { id }
                }
            }
            """;

    void onOpen(@PullRequest.Opened GHEventPayload.PullRequest payload, DynamicGraphQLClient client) throws IOException {
        link(payload, client);
    }

    void onEdit(@PullRequest.Edited GHEventPayload.PullRequest payload, DynamicGraphQLClient client) throws IOException {
        link(payload, client);
    }

    void onReopen(@PullRequest.Reopened GHEventPayload.PullRequest payload, DynamicGraphQLClient client) throws IOException {
        link(payload, client);
    }

    private void link(GHEventPayload.PullRequest payload, DynamicGraphQLClient client) throws IOException {
        GHRepository repository = payload.getRepository();
        GHPullRequest pr = payload.getPullRequest();
        try {
            String branch = pr.getBase().getRef();
            if (!branch.startsWith("release/")) {
                return;
            }
            Set<Integer> issueNumbers = PullRequestParser.closingIssues(pr.getBody(), repository.getFullName());
            Set<Integer> issuesToUpdate = new LinkedHashSet<>(issueNumbers);
            // Include previous references so description edits can remove stale links.
            GHPullRequestChanges changes = payload.getChanges();
            if (changes != null && changes.getBody() != null) {
                issuesToUpdate.addAll(PullRequestParser.closingIssues(changes.getBody().getFrom(), repository.getFullName()));
            }
            // Maintain links for both open and closed PRs.
            for (int issueNumber : issuesToUpdate) {
                GHIssue issue;
                try {
                    issue = repository.getIssue(issueNumber);
                } catch (GHFileNotFoundException e) {
                    logger.errorv(e, "Referenced issue not found while linking backport PR {0}#{1} to issue #{2}",
                            repository.getFullName(), pr.getNumber(), issueNumber);
                    continue;
                }
                if (!issue.isPullRequest()) {
                    boolean link = issueNumbers.contains(issueNumber);
                    updateLink(client, link ? LINK_PR : UNLINK_PR, issue.getNodeId(), pr.getNodeId());
                    logger.infov("Backport PR {0}#{1} {2} issue {0}#{3}",
                            repository.getFullName(), pr.getNumber(), link ? "linked to" : "unlinked from", issueNumber);
                }
            }
        } catch (Exception e) {
            logger.errorv(e, "Failed to link backport PR {0}#{1} to referenced issues",
                    repository.getFullName(), pr.getNumber());
            throw e;
        }
    }

    private void updateLink(DynamicGraphQLClient client, String mutation, String issueId, String prId) throws IOException {
        try {
            Response response = client.executeSync(mutation, Map.of("issueId", issueId, "pullRequestIds", List.of(prId)));
            if (response.hasError() || !response.hasData()) {
                throw new IOException("Backport GraphQL request failed: " + response.getErrors());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Backport linking interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Backport GraphQL request failed", e);
        }
    }
}
