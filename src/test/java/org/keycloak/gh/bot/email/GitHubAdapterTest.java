package org.keycloak.gh.bot.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.kohsuke.github.GHIssueSearchBuilder;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GitHubAdapterTest {

    GitHubAdapter gitHubAdapter;
    GitHubInstallationProvider mockInstallationProvider;

    @BeforeEach
    public void setup() {
        gitHubAdapter = new GitHubAdapter();
        mockInstallationProvider = mock(GitHubInstallationProvider.class);

        gitHubAdapter.gitHubProvider = mockInstallationProvider;
    }

    @Test
    public void testFindIssueQueryStructure() throws IOException {
        String repoName = "keycloak/keycloak-private";
        String threadId = "abc12345";

        GitHub mockGitHub = mock(GitHub.class);
        GHIssueSearchBuilder mockSearch = mock(GHIssueSearchBuilder.class);

        when(mockInstallationProvider.getRepositoryFullName()).thenReturn(repoName);
        when(mockInstallationProvider.getGitHub()).thenReturn(mockGitHub);

        when(mockGitHub.searchIssues()).thenReturn(mockSearch);
        when(mockSearch.q(anyString())).thenReturn(mockSearch);
        when(mockSearch.list()).thenReturn(mock(PagedSearchIterable.class));

        gitHubAdapter.findIssueByThreadId(threadId);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSearch).q(queryCaptor.capture());

        String query = queryCaptor.getValue();
        assertTrue(query.contains("repo:" + repoName));
        assertTrue(query.contains("\"" + threadId + "\""));
        assertTrue(query.contains("in:comments"));
    }
}