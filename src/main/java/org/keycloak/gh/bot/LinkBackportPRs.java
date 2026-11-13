package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.PullRequestParser;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
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
            if (pr.getState() != GHIssueState.OPEN || !branch.startsWith("release/")) {
                return;
            }
            Set<Integer> issueNumbers = PullRequestParser.closingIssues(pr.getBody(), repository.getFullName());
            for (int issueNumber : issueNumbers) {
                GHIssue issue;
                try {
                    issue = repository.getIssue(issueNumber);
                } catch (GHFileNotFoundException e) {
                    logger.errorv(e, "Referenced issue not found while linking backport PR {0}#{1} to issue #{2}",
                            repository.getFullName(), pr.getNumber(), issueNumber);
                    continue;
                }
                if (!issue.isPullRequest()) {
                    link(client, issue.getNodeId(), pr.getNodeId());
                    logger.infov("Linked backport PR {0}#{1} to issue {0}#{2}",
                            repository.getFullName(), pr.getNumber(), issueNumber);
                }
            }
        } catch (Exception e) {
            logger.errorv(e, "Failed to link backport PR {0}#{1} to referenced issues",
                    repository.getFullName(), pr.getNumber());
            throw e;
        }
    }

    private void link(DynamicGraphQLClient client, String issueId, String prId) throws IOException {
        try {
            Response response = client.executeSync(LINK_PR, Map.of("issueId", issueId, "pullRequestIds", List.of(prId)));
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
