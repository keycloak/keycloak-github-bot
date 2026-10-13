package org.keycloak.gh.bot;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkBackportPRsTest {

    private final LinkBackportPRs handler = new LinkBackportPRs();
    private final DynamicGraphQLClient client = mock(DynamicGraphQLClient.class);
    private final GHEventPayload.PullRequest payload = mock(GHEventPayload.PullRequest.class);
    private final GHRepository repository = mock(GHRepository.class);
    private final GHPullRequest pr = mock(GHPullRequest.class);
    private final GHCommitPointer base = mock(GHCommitPointer.class);
    private final GHIssue issue = mock(GHIssue.class);

    @BeforeEach
    void setUp() throws Exception {
        when(payload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("keycloak/keycloak");
        when(payload.getPullRequest()).thenReturn(pr);
        when(pr.getState()).thenReturn(GHIssueState.OPEN);
        when(pr.getBody()).thenReturn("Closes #123");
        when(pr.getBase()).thenReturn(base);
        when(base.getRef()).thenReturn("release/26.8");
        when(pr.getNodeId()).thenReturn("backport");
        when(repository.getIssue(123)).thenReturn(issue);
        when(issue.getNodeId()).thenReturn("issue");
        Response response = mock(Response.class);
        when(response.hasData()).thenReturn(true);
        when(client.executeSync(contains("mutation"), anyMap())).thenReturn(response);
    }

    @Test
    void linksOnOpenEditAndReopen() throws Exception {
        handler.onOpen(payload, client);
        handler.onEdit(payload, client);
        handler.onReopen(payload, client);
        verify(client, times(3)).executeSync(contains("mutation"),
                eq(Map.of("issueId", "issue", "pullRequestIds", List.of("backport"))));
    }

    @ParameterizedTest
    @CsvSource({"main,OPEN", "release/26.8,CLOSED"})
    void skipsUnrelatedOrClosedPRs(String branch, GHIssueState state) throws Exception {
        when(base.getRef()).thenReturn(branch);
        when(pr.getState()).thenReturn(state);
        handler.onOpen(payload, client);
        verifyNoInteractions(client);
    }

    @Test
    void linksIssuesInAnyRepository() throws Exception {
        when(repository.getFullName()).thenReturn("other/repo");
        handler.onOpen(payload, client);
        verify(repository).getIssue(123);
        verify(client).executeSync(contains("mutation"),
                eq(Map.of("issueId", "issue", "pullRequestIds", List.of("backport"))));
    }

    @ParameterizedTest
    @CsvSource({"Closes other/repo#123", "Resolves https://github.com/other/repo/issues/123"})
    void skipsCrossRepositoryReferences(String body) throws Exception {
        when(pr.getBody()).thenReturn(body);
        handler.onOpen(payload, client);
        verify(repository, times(0)).getIssue(123);
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @CsvSource({"keycloak/keycloak,Fixes keycloak/keycloak#123",
            "keycloak/keycloak,Resolves https://github.com/keycloak/keycloak/issues/123",
            "other/repo,Fixes other/repo#123",
            "other/repo,Closes https://github.com/other/repo/issues/123"})
    void linksQualifiedReferencesInCurrentRepository(String repo, String body) throws Exception {
        when(repository.getFullName()).thenReturn(repo);
        when(pr.getBody()).thenReturn(body);
        handler.onOpen(payload, client);
        verify(repository).getIssue(123);
        verify(client).executeSync(contains("mutation"),
                eq(Map.of("issueId", "issue", "pullRequestIds", List.of("backport"))));
    }

    @Test
    void skipsMissingIssuesAndPullRequestReferences() throws Exception {
        when(pr.getBody()).thenReturn("Closes #999\nCloses #123");
        when(repository.getIssue(999)).thenThrow(GHFileNotFoundException.class);
        handler.onOpen(payload, client);
        verify(client).executeSync(contains("mutation"), anyMap());
        when(issue.isPullRequest()).thenReturn(true);
        handler.onEdit(payload, client);
        verify(client, times(1)).executeSync(contains("mutation"), anyMap());
    }

    @Test
    void propagatesGraphQLErrors() throws Exception {
        Response error = mock(Response.class);
        when(error.hasError()).thenReturn(true);
        when(client.executeSync(contains("mutation"), anyMap())).thenReturn(error);
        assertThrows(IOException.class, () -> handler.onOpen(payload, client));
    }
}
