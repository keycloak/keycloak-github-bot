package org.keycloak.gh.bot.security.jira;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.common.GitHubAdapter;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class JiraProcessorTest {

    @Inject JiraProcessor processor;

    @InjectMock GitHubAdapter github;
    @InjectMock JiraAdapter jira;

    @Inject
    org.keycloak.gh.bot.security.jira.JiraIssueParser parser;

    @Test
    public void testProcessJiraUpdates_SyncsMatchingIssue() throws IOException {
        String cve = "CVE-2023-5555";

        GHIssue ghIssue = mock(GHIssue.class);
        when(ghIssue.getNumber()).thenReturn(101);
        when(ghIssue.getTitle()).thenReturn(cve + " Placeholder Title");
        when(ghIssue.getBody()).thenReturn("Placeholder body");

        when(github.getOpenCveIssues()).thenReturn(List.of(ghIssue));

        JiraAdapter.JiraIssue jiraIssue = new JiraAdapter.JiraIssue("RHBK-99", cve + " Real Title", "Flaw:\nReal Body");
        when(jira.findIssueByCve(cve)).thenReturn(Optional.of(jiraIssue));

        processor.processJiraUpdates();

        verify(ghIssue).setTitle(cve + " Real Title");
        verify(ghIssue).setBody("## Description\n\nReal Body");
        verify(ghIssue).addLabels(Constants.KIND_CVE);
        verify(ghIssue).addLabels(Constants.STATUS_JIRA_SYNCED);
    }

    @Test
    public void testProcessJiraUpdates_SkipsIfNoChange() throws IOException {
        String cve = "CVE-2023-5555";
        String syncedTitle = cve + " Real Title";
        String syncedBody = "## Description\n\nReal Body";

        GHIssue ghIssue = mock(GHIssue.class);
        when(ghIssue.getTitle()).thenReturn(syncedTitle);
        when(ghIssue.getBody()).thenReturn(syncedBody);

        when(github.getOpenCveIssues()).thenReturn(List.of(ghIssue));

        JiraAdapter.JiraIssue jiraIssue = new JiraAdapter.JiraIssue("RHBK-99", syncedTitle, "Flaw:\nReal Body");
        when(jira.findIssueByCve(cve)).thenReturn(Optional.of(jiraIssue));

        processor.processJiraUpdates();

        verify(ghIssue, never()).setTitle(anyString());
        verify(ghIssue, never()).setBody(anyString());
    }

    @Test
    public void testProcessJiraUpdates_SkipsIfJiraNotFound() throws IOException {
        String cve = "CVE-2023-5555";
        GHIssue ghIssue = mock(GHIssue.class);
        when(ghIssue.getTitle()).thenReturn(cve);

        when(github.getOpenCveIssues()).thenReturn(List.of(ghIssue));
        when(jira.findIssueByCve(cve)).thenReturn(Optional.empty());

        processor.processJiraUpdates();

        verify(ghIssue, never()).setTitle(anyString());
    }
}