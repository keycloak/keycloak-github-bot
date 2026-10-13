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
                logger.warnv("Referenced issue not found: repository={0}, issue={1}", repository.getFullName(), issueNumber);
                continue;
            }
            if (!issue.isPullRequest()) {
                link(client, issue.getNodeId(), pr.getNodeId());
            }
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
