package org.keycloak.gh.bot;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackportPullRequestsTest {

    private DynamicGraphQLClient client;
    private BackportPullRequests service;
    private GHIssue issue;

    @BeforeEach
    void setUp() throws IOException {
        client = mock(DynamicGraphQLClient.class);
        service = new BackportPullRequests();
        service.gitHubProvider = mock(GitHubInstallationProvider.class);
        when(service.gitHubProvider.getGraphQLClient("keycloak/keycloak")).thenReturn(client);
        issue = mock(GHIssue.class);
        GHRepository repository = mock(GHRepository.class);
        when(repository.getFullName()).thenReturn("keycloak/keycloak");
        when(issue.getRepository()).thenReturn(repository);
        when(issue.getNodeId()).thenReturn("issue");
        when(issue.getNumber()).thenReturn(123);
    }

    @Test
    void recognizesClosingReferences() {
        assertTrue(BackportPullRequests.closingIssues("Closes #123", "keycloak/keycloak").contains(123));
        assertTrue(BackportPullRequests.closingIssues("FIXES: keycloak/keycloak#123", "keycloak/keycloak").contains(123));
        assertTrue(BackportPullRequests.closingIssues("Resolves https://github.com/keycloak/keycloak/issues/123", "keycloak/keycloak").contains(123));
        assertFalse(BackportPullRequests.closingIssues("Related to #123", "keycloak/keycloak").contains(123));
        assertFalse(BackportPullRequests.closingIssues("Closes other/repo#123", "keycloak/keycloak").contains(123));
        assertFalse(BackportPullRequests.closingIssues("Closes #1234", "keycloak/keycloak").contains(123));
    }

    @Test
    void parsesMultipleReferencesAndIgnoresInvalidIssueNumbers() {
        assertEquals(Set.of(123, 456), BackportPullRequests.closingIssues(
                "Closes #123\nFixes keycloak/keycloak#456\nResolves #123\nCloses other/repo#789\nCloses #0\nCloses #999999999999999999999",
                "keycloak/keycloak"));
        assertTrue(BackportPullRequests.closingIssues(null, "keycloak/keycloak").isEmpty());
    }

    @Test
    void linksWebhookPullRequestDirectlyWithoutMainOrTimelineChecks() throws Exception {
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.getNodeId()).thenReturn("backport");
        Response linked = page(List.of(), false);
        Response mutation = mutationResponse();
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked, mutation);

        service.link(issue, pr);

        verify(client).executeSync(contains("mutation"), eq(Map.of("issueId", "issue", "pullRequestIds", List.of("backport"))));
        verify(client, never()).executeSync(contains("timelineItems"), anyMap());
    }

    @Test
    void doesNotDuplicateExistingDevelopmentLink() throws Exception {
        GHPullRequest pr = mock(GHPullRequest.class);
        when(pr.getNodeId()).thenReturn("backport");
        Response linked = page(List.of(pr("backport", "release/26.8", "OPEN")), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked);

        service.link(issue, pr);

        verify(client, never()).executeSync(contains("mutation"), anyMap());
    }

    @Test
    void acceptsOpenLinkedBackportAndRejectsUnmergedClosedBackport() throws Exception {
        Response linked = page(List.of(pr("main", "main", "MERGED"),
                pr("backport", "release/26.6", "OPEN"), pr("abandoned", "release/26.7", "CLOSED")), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked);

        BackportPullRequests.Result result = service.check(issue, Set.of("release/26.6", "release/26.7"));

        assertTrue(result.mainMerged());
        assertEquals(Set.of("release/26.7"), result.missingBranches());
        verify(client, never()).executeSync(contains("mutation"), anyMap());
    }

    @Test
    void acceptsMergedLinkedBackportWithoutKeyword() throws Exception {
        Response linked = page(List.of(pr("main", "main", "MERGED"), pr("backport", "release/26.8", "MERGED")), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked);
        assertTrue(service.check(issue, Set.of("release/26.8")).missingBranches().isEmpty());
        verify(client, times(1)).executeSync(anyString(), anyMap());
        verify(client, never()).executeSync(contains("timelineItems"), anyMap());
    }

    @Test
    void readsAllPagesOfDevelopmentLinks() throws Exception {
        Response firstPage = page(List.of(), true);
        Response secondPage = page(List.of(pr("main", "main", "MERGED")), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(firstPage, secondPage);
        assertEquals(Set.of("release/26.8"), service.check(issue, Set.of("release/26.8")).missingBranches());
        verify(client, times(2)).executeSync(contains("closedByPullRequestsReferences"), anyMap());
        verify(service.gitHubProvider).getGraphQLClient("keycloak/keycloak");
        verify(client, never()).executeSync(contains("mutation"), anyMap());
    }

    @Test
    void failsInsteadOfRemindingAfterApiError() throws Exception {
        Response error = mock(Response.class);
        when(error.hasError()).thenReturn(true);
        when(client.executeSync(anyString(), anyMap())).thenReturn(error);
        assertThrows(IOException.class, () -> service.check(issue, Set.of("release/26.8")));
    }

    @Test
    void ignoresPullRequestsInOtherRepositories() throws Exception {
        JsonObject unrelated = Json.createObjectBuilder(pr("unrelated", "release/26.8", "OPEN"))
                .add("repository", Json.createObjectBuilder().add("nameWithOwner", "other/repo")).build();
        Response linked = page(List.of(pr("main", "main", "MERGED"), unrelated), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked);

        assertEquals(Set.of("release/26.8"), service.check(issue, Set.of("release/26.8")).missingBranches());
        verify(client, never()).executeSync(contains("mutation"), anyMap());
    }

    @Test
    void scheduledCheckOnlyReadsDevelopmentLinks() throws Exception {
        Response linked = page(List.of(pr("main", "main", "MERGED")), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked);

        BackportPullRequests.Result result = service.check(issue, Set.of("release/26.8"));

        assertTrue(result.mainMerged());
        assertEquals(Set.of("release/26.8"), result.missingBranches());
        verify(client, times(1)).executeSync(contains("closedByPullRequestsReferences"), anyMap());
        verify(client, never()).executeSync(contains("timelineItems"), anyMap());
        verify(client, never()).executeSync(contains("mutation"), anyMap());
    }

    @Test
    void requiresMergedMainPullRequest() throws Exception {
        Response linked = page(List.of(pr("main", "main", "OPEN")), false);
        when(client.executeSync(anyString(), anyMap())).thenReturn(linked);
        assertFalse(service.check(issue, Set.of("release/26.8")).mainMerged());
        verify(client, never()).executeSync(contains("mutation"), anyMap());
    }

    private JsonObject pr(String id, String branch, String state) {
        return Json.createObjectBuilder().add("id", id).add("baseRefName", branch).add("state", state)
                .add("repository", Json.createObjectBuilder().add("nameWithOwner", "keycloak/keycloak")).build();
    }

    private Response page(List<JsonObject> prs, boolean hasNextPage) {
        JsonArrayBuilder nodes = Json.createArrayBuilder();
        for (JsonObject pr : prs) {
            nodes.add(pr);
        }
        Response response = mock(Response.class);
        when(response.hasData()).thenReturn(true);
        when(response.getData()).thenReturn(Json.createObjectBuilder().add("node", Json.createObjectBuilder()
                .add("pullRequests", Json.createObjectBuilder().add("nodes", nodes)
                        .add("pageInfo", Json.createObjectBuilder().add("hasNextPage", hasNextPage).add("endCursor", "next")))).build());
        return response;
    }

    private Response mutationResponse() {
        Response response = mock(Response.class);
        when(response.hasData()).thenReturn(true);
        when(response.getData()).thenReturn(Json.createObjectBuilder().build());
        return response;
    }
}
