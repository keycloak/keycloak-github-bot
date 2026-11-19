package org.keycloak.gh.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AddReleaseLabelOnMergeTest {

    private final AddReleaseLabelOnMerge handler = new AddReleaseLabelOnMerge();
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
        when(pr.isMerged()).thenReturn(true);
        when(pr.getNumber()).thenReturn(1);
        when(pr.getBody()).thenReturn("Closes #123");
        when(pr.getBase()).thenReturn(base);
        when(base.getRef()).thenReturn("main");
        when(repository.getBranches()).thenReturn(Map.of(
                "main", mock(GHBranch.class), "release/26.7", mock(GHBranch.class), "release/26.8", mock(GHBranch.class)));
        when(repository.getIssue(123)).thenReturn(issue);
        issueLabels("backport/main");
        repositoryLabels("backport/main");
    }

    @Test
    void mainCreatesLabelAndMovesIssue() throws Exception {
        handler.onClose(payload);

        verify(repository).createLabel("release/27.0.0", "0E8A16");
        verify(issue).addLabels("release/27.0.0");
        verify(issue).removeLabel("backport/main");
        verify(repository, never()).listTags();
    }

    @Test
    void releaseBranchUsesNextMicroAndKeepsOtherBackportLabels() throws Exception {
        when(base.getRef()).thenReturn("release/26.8");
        repositoryTags("26.8.0", "26.8.2", "26.7.5", "nightly");
        issueLabels("backport/26.8", "backport/main");

        handler.onClose(payload);

        verify(repository).createLabel("release/26.8.3", "0E8A16");
        verify(issue).addLabels("release/26.8.3");
        verify(issue).removeLabel("backport/26.8");
        verify(issue, never()).removeLabel("backport/main");
        verify(repository, never()).getBranches();
    }

    @Test
    void existingLabelIsNotCreatedAgain() throws Exception {
        repositoryLabels("release/27.0.0");

        handler.onClose(payload);

        verify(repository, never()).createLabel(anyString(), anyString());
        verify(issue).addLabels("release/27.0.0");
    }

    @Test
    void labelCreatedConcurrentlyIsAccepted() throws Exception {
        when(repository.createLabel("release/27.0.0", "0E8A16")).thenAnswer(i -> {
            repositoryLabels("release/27.0.0");
            throw new IOException("already exists");
        });

        handler.onClose(payload);

        verify(issue).addLabels("release/27.0.0");
    }

    @Test
    void labelCreationFailureIsPropagated() throws Exception {
        when(repository.createLabel("release/27.0.0", "0E8A16")).thenThrow(new IOException("forbidden"));

        assertThrows(IOException.class, () -> handler.onClose(payload));

        verify(issue, never()).addLabels(anyString());
    }

    @Test
    void issueWithoutBackportLabelOnlyGetsReleaseLabel() throws Exception {
        issueLabels();

        handler.onClose(payload);

        verify(issue).addLabels("release/27.0.0");
        verify(issue, never()).removeLabel(anyString());
    }

    @Test
    void missingIssuesAndPullRequestsAreSkipped() throws Exception {
        GHIssue otherPr = mock(GHIssue.class);
        when(otherPr.isPullRequest()).thenReturn(true);
        when(repository.getIssue(999)).thenThrow(new GHFileNotFoundException("not found"));
        when(repository.getIssue(456)).thenReturn(otherPr);
        when(pr.getBody()).thenReturn("Closes #999\nCloses #456\nFixes #123");

        handler.onClose(payload);

        verify(issue).addLabels("release/27.0.0");
        verify(otherPr, never()).addLabels(anyString());
        verify(otherPr, never()).removeLabel(anyString());
    }

    @Test
    void commitMessageIsUsedWhenDescriptionHasNoIssue() throws Exception {
        GHCommit commit = mock(GHCommit.class);
        GHCommit.ShortInfo info = mock(GHCommit.ShortInfo.class);
        when(pr.getBody()).thenReturn("No closing keyword here");
        when(pr.getMergeCommitSha()).thenReturn("abc");
        when(repository.getCommit("abc")).thenReturn(commit);
        when(commit.getCommitShortInfo()).thenReturn(info);
        when(info.getMessage()).thenReturn("Some change\n\nCloses #123");

        handler.onClose(payload);

        verify(issue).addLabels("release/27.0.0");
    }

    @Test
    void commitIsNotFetchedWhenDescriptionHasIssue() throws Exception {
        when(pr.getBody()).thenReturn("Closes #123");
        when(pr.getMergeCommitSha()).thenReturn("abc");

        handler.onClose(payload);

        verify(issue).addLabels("release/27.0.0");
        verify(repository, never()).getCommit(anyString());
    }

    @Test
    void unmergedPRsAreIgnored() throws Exception {
        when(pr.isMerged()).thenReturn(false);

        handler.onClose(payload);

        verifyNoInteractions(repository);
    }

    @Test
    void unrelatedBranchesAreIgnored() throws Exception {
        when(base.getRef()).thenReturn("feature/x");

        handler.onClose(payload);

        verify(repository, never()).getBranches();
        verify(repository, never()).listTags();
        verify(repository, never()).getIssue(123);
    }

    @Test
    void noLinkedIssuesDoesNothing() throws Exception {
        when(pr.getBody()).thenReturn("Just a change");

        handler.onClose(payload);

        verify(repository, never()).createLabel(anyString(), anyString());
        verify(repository, never()).getIssue(123);
    }

    private GHLabel label(String name) {
        GHLabel label = mock(GHLabel.class);
        when(label.getName()).thenReturn(name);
        return label;
    }

    @SuppressWarnings("unchecked")
    private void repositoryLabels(String... names) throws IOException {
        List<GHLabel> labels = Arrays.stream(names).map(this::label).toList();
        PagedIterable<GHLabel> iterable = mock(PagedIterable.class);
        when(iterable.withPageSize(anyInt())).thenReturn(iterable);
        when(iterable.iterator()).thenAnswer(i -> {
            Iterator<GHLabel> delegate = labels.iterator();
            PagedIterator<GHLabel> iterator = mock(PagedIterator.class);
            when(iterator.hasNext()).thenAnswer(x -> delegate.hasNext());
            when(iterator.next()).thenAnswer(x -> delegate.next());
            return iterator;
        });
        when(repository.listLabels()).thenReturn(iterable);
    }

    private void issueLabels(String... names) {
        List<GHLabel> labels = Arrays.stream(names).map(this::label).toList();
        when(issue.getLabels()).thenReturn(labels);
    }

    @SuppressWarnings("unchecked")
    private void repositoryTags(String... names) throws IOException {
        List<GHTag> tags = Arrays.stream(names).map(name -> {
            GHTag tag = mock(GHTag.class);
            when(tag.getName()).thenReturn(name);
            return tag;
        }).toList();
        PagedIterable<GHTag> iterable = mock(PagedIterable.class);
        when(iterable.toList()).thenReturn(tags);
        when(repository.listTags()).thenReturn(iterable);
    }

}
