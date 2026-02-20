package org.keycloak.gh.bot.security.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentUpdateResponse;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueSearchBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GitHubAdapter ensuring correct query structure for security reports.
 */
public class GitHubAdapterTest {

    private GitHubAdapter adapter;
    private GitHubInstallationProvider mockInstallationProvider;

    @BeforeEach
    public void setup() {
        adapter = spy(new GitHubAdapter());
        mockInstallationProvider = mock(GitHubInstallationProvider.class);
        adapter.gitHubProvider = mockInstallationProvider;
    }

    @Test
    public void testFindOpenEmailIssueQuery() throws IOException {
        String repoName = "keycloak/keycloak-private";
        adapter.allowedRepository = repoName;
        GitHub mockGitHub = mock(GitHub.class);
        GHIssueSearchBuilder mockSearch = mock(GHIssueSearchBuilder.class);
        PagedSearchIterable<GHIssue> mockIterable = mock(PagedSearchIterable.class);
        PagedIterator<GHIssue> mockIterator = mock(PagedIterator.class);

        when(mockInstallationProvider.getRepositoryFullName()).thenReturn(repoName);
        when(mockInstallationProvider.getGitHub()).thenReturn(mockGitHub);
        when(mockGitHub.searchIssues()).thenReturn(mockSearch);
        when(mockSearch.q(anyString())).thenReturn(mockSearch);
        when(mockSearch.list()).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockIterator.hasNext()).thenReturn(false);

        adapter.findOpenEmailIssueByThreadId("thread-123");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSearch).q(queryCaptor.capture());

        String query = queryCaptor.getValue();
        assertTrue(query.contains("repo:" + repoName));
        assertTrue(query.contains("label:" + Constants.SOURCE_EMAIL));
        assertTrue(query.contains("is:open"));
        assertTrue(query.contains("\"thread-123\""));
    }

    @Test
    public void testUploadFile() throws IOException {
        String repoName = "keycloak/keycloak-private";
        adapter.allowedRepository = repoName;
        String threadId = "thread-123";
        String fileName = "poc.txt";
        byte[] content = "exploit".getBytes();
        String expectedPath = "attachments/" + threadId + "/" + fileName;
        String expectedUrl = "https://github.com/" + repoName + "/blob/main/" + expectedPath;

        GHContentUpdateResponse mockResponse = mock(GHContentUpdateResponse.class);
        GHContent mockContent = mock(GHContent.class);

        when(mockInstallationProvider.getRepositoryFullName()).thenReturn(repoName);
        when(mockResponse.getContent()).thenReturn(mockContent);
        when(mockContent.getHtmlUrl()).thenReturn(expectedUrl);

        doReturn(mockResponse).when(adapter).uploadToRepo(anyString(), any(byte[].class), anyString());

        String resultUrl = adapter.uploadFile(threadId, fileName, content);

        assertEquals(expectedUrl, resultUrl);
        verify(adapter).uploadToRepo(expectedPath, content, "Upload attachment " + fileName + " for thread " + threadId);
    }
}