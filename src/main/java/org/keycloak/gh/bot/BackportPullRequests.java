package org.keycloak.gh.bot;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.json.JsonObject;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class BackportPullRequests {

    private static final String LINKED_PRS = """
            query($issueId: ID!, $cursor: String) {
                node(id: $issueId) {
                    ... on Issue {
                        pullRequests: closedByPullRequestsReferences(first: 100, after: $cursor, includeClosedPrs: true) {
                            nodes { id baseRefName state repository { nameWithOwner } }
                            pageInfo { hasNextPage endCursor }
                        }
                    }
                }
            }
            """;

    private static final String LINK_PR = """
            mutation($issueId: ID!, $pullRequestIds: [ID!]!) {
                addCloseIssueReferences(input: {issueId: $issueId, pullRequestIds: $pullRequestIds}) {
                    issue { id }
                }
            }
            """;

    private static final Pattern CLOSING_REFERENCE = Pattern.compile(
            "(?i)\\b(?:close[sd]?|fix(?:es|ed)?|resolve[sd]?)\\s*:?\\s+"
                    + "(?:(?:([\\w.-]+/[\\w.-]+))?#(\\d+)|https://github\\.com/([\\w.-]+/[\\w.-]+)/issues/(\\d+))"
                    + "(?![\\w/])");

    @Inject
    GitHubInstallationProvider gitHubProvider;

    public Result check(GHIssue issue, Set<String> requiredBranches) throws IOException {
        String repository = issue.getRepository().getFullName();
        DynamicGraphQLClient client = gitHubProvider.getGraphQLClient(repository);
        List<PullRequest> linked = findLinkedPullRequests(client, issue.getNodeId(), repository);
        boolean mainMerged = linked.stream().anyMatch(PullRequest::isMergedMain);
        Set<String> missing = new TreeSet<>(requiredBranches);
        for (PullRequest pr : linked) {
            if (pr.isAvailable()) {
                missing.remove(pr.branch());
            }
        }
        return new Result(mainMerged, missing);
    }

    public void link(GHIssue issue, GHPullRequest pullRequest) throws IOException {
        String repository = issue.getRepository().getFullName();
        DynamicGraphQLClient client = gitHubProvider.getGraphQLClient(repository);
        List<PullRequest> linked = findLinkedPullRequests(client, issue.getNodeId(), repository);
        if (linked.stream().noneMatch(pr -> pr.id().equals(pullRequest.getNodeId()))) {
            execute(client, LINK_PR, Map.of("issueId", issue.getNodeId(), "pullRequestIds", List.of(pullRequest.getNodeId())));
        }
    }

    static Set<Integer> closingIssues(String body, String repository) {
        Set<Integer> issues = new LinkedHashSet<>();
        if (body == null) {
            return issues;
        }
        Matcher matcher = CLOSING_REFERENCE.matcher(body);
        while (matcher.find()) {
            String referenceRepository = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String number = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            if (referenceRepository == null || referenceRepository.equalsIgnoreCase(repository)) {
                try {
                    int issueNumber = Integer.parseInt(number);
                    if (issueNumber > 0) {
                        issues.add(issueNumber);
                    }
                } catch (NumberFormatException e) {
                    // Ignore issue numbers outside the client's integer range.
                }
            }
        }
        return issues;
    }

    private List<PullRequest> findLinkedPullRequests(DynamicGraphQLClient client, String issueId, String repository) throws IOException {
        List<PullRequest> result = new ArrayList<>();
        Map<String, Object> variables = new HashMap<>();
        variables.put("issueId", issueId);
        boolean hasNextPage;
        do {
            JsonObject node = execute(client, LINKED_PRS, variables).getJsonObject("node");
            if (node == null) {
                throw new IOException("Issue not found: " + issueId);
            }
            JsonObject connection = node.getJsonObject("pullRequests");
            for (JsonObject pr : connection.getJsonArray("nodes").getValuesAs(JsonObject.class)) {
                if (pr != null && pr.containsKey("baseRefName")
                        && pr.getJsonObject("repository").getString("nameWithOwner").equalsIgnoreCase(repository)) {
                    result.add(new PullRequest(pr.getString("id"), pr.getString("baseRefName"), pr.getString("state")));
                }
            }
            JsonObject pageInfo = connection.getJsonObject("pageInfo");
            hasNextPage = pageInfo.getBoolean("hasNextPage");
            if (hasNextPage) {
                variables.put("cursor", pageInfo.getString("endCursor"));
            }
        } while (hasNextPage);
        return result;
    }

    private JsonObject execute(DynamicGraphQLClient client, String query, Map<String, Object> variables) throws IOException {
        try {
            Response response = client.executeSync(query, variables);
            if (response.hasError() || !response.hasData()) {
                throw new IOException("Backport GraphQL request failed: " + response.getErrors());
            }
            return response.getData();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Backport check interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Backport GraphQL request failed", e);
        }
    }

    public record Result(boolean mainMerged, Set<String> missingBranches) {
    }

    private record PullRequest(String id, String branch, String state) {

        boolean isMergedMain() {
            return branch.equals("main") && state.equals("MERGED");
        }

        boolean isAvailable() {
            return state.equals("OPEN") || state.equals("MERGED");
        }
    }
}
