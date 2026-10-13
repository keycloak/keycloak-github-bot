package org.keycloak.gh.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LinkBackportPRsTest {

    private LinkBackportPRs handler;
    private GHEventPayload.PullRequest payload;
    private GHRepository repository;
    private GHPullRequest pr;
    private GHCommitPointer base;
    private GHIssue issue;

    @BeforeEach
    void setUp() throws IOException {
        handler = new LinkBackportPRs();
        handler.mainRepository = "keycloak/keycloak";
        handler.pullRequests = mock(BackportPullRequests.class);
        payload = mock(GHEventPayload.PullRequest.class);
        repository = mock(GHRepository.class);
        when(payload.getRepository()).thenReturn(repository);
        when(repository.getFullName()).thenReturn("keycloak/keycloak");
        when(repository.getBranches()).thenReturn(Map.of());
        pr = mock(GHPullRequest.class);
        when(payload.getPullRequest()).thenReturn(pr);
        when(pr.getState()).thenReturn(GHIssueState.OPEN);
        when(pr.getBody()).thenReturn("Closes #123");
        base = mock(GHCommitPointer.class);
        when(pr.getBase()).thenReturn(base);
        when(base.getRef()).thenReturn("release/26.8");
        issue = mock(GHIssue.class);
        when(repository.getIssue(123)).thenReturn(issue);
        when(issue.getState()).thenReturn(GHIssueState.CLOSED);
        when(issue.getStateReason()).thenReturn(GHIssueStateReason.COMPLETED);
        GHLabel label = mock(GHLabel.class);
        when(label.getName()).thenReturn("backport/26.8");
        when(issue.getLabels()).thenReturn(List.of(label));
    }

    @Test
    void linksOnOpenWithoutWaitingForClosureAge() throws IOException {
        handler.onOpen(payload);
        verify(handler.pullRequests).link(issue, pr);
        verify(issue, never()).getClosedAt();
    }

    @Test
    void linksWhenDescriptionIsEditedToReferenceIssue() throws IOException {
        when(pr.getBody()).thenReturn("Backport of the fix");
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);

        when(pr.getBody()).thenReturn("Fixes keycloak/keycloak#123");
        handler.onEdit(payload);
        verify(handler.pullRequests).link(issue, pr);
    }

    @Test
    void linksWhenBaseBranchChangesToRelease() throws IOException {
        when(base.getRef()).thenReturn("main");
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);

        when(base.getRef()).thenReturn("release/26.8");
        handler.onEdit(payload);
        verify(handler.pullRequests).link(issue, pr);
    }

    @Test
    void linksOnReopen() throws IOException {
        handler.onReopen(payload);
        verify(handler.pullRequests).link(issue, pr);
    }

    @Test
    void skipsOtherRepositories() throws IOException {
        when(repository.getFullName()).thenReturn("other/repo");
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);
        verify(repository, never()).getBranches();
    }

    @Test
    void skipsArchivedReleaseBeforeIssueLookups() throws IOException {
        GHBranch archived = mock(GHBranch.class);
        when(repository.getBranches()).thenReturn(Map.of("archive/release/26.8", archived));
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);
        verify(repository, never()).getIssue(anyInt());
    }

    @Test
    void skipsMissingOrForeignClosingReferencesBeforeBranchLookups() throws IOException {
        for (String body : new String[] {null, "Related to #123", "Closes other/repo#123"}) {
            when(pr.getBody()).thenReturn(body);
            handler.onOpen(payload);
        }
        verifyNoInteractions(handler.pullRequests);
        verify(repository, never()).getBranches();
    }

    @Test
    void requiresMatchingBackportLabel() throws IOException {
        when(issue.getLabels()).thenReturn(List.of());
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);
    }

    @Test
    void skipsNotPlannedIssues() throws IOException {
        when(issue.getStateReason()).thenReturn(GHIssueStateReason.NOT_PLANNED);
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);
    }

    @Test
    void skipsOpenIssues() throws IOException {
        when(issue.getState()).thenReturn(GHIssueState.OPEN);
        handler.onOpen(payload);
        verifyNoInteractions(handler.pullRequests);
    }

    @Test
    void skipsClosedPullRequests() throws IOException {
        when(pr.getState()).thenReturn(GHIssueState.CLOSED);
        handler.onEdit(payload);
        verifyNoInteractions(handler.pullRequests);
        verify(repository, never()).getBranches();
    }

    @Test
    void linksValidReferencesWhenAnotherIssueDoesNotExist() throws IOException {
        when(pr.getBody()).thenReturn("Closes #999\nCloses #123\nFixes #123");
        when(repository.getIssue(999)).thenThrow(GHFileNotFoundException.class);
        handler.onOpen(payload);
        verify(handler.pullRequests).link(issue, pr);
        verify(repository).getIssue(123);
    }
}
