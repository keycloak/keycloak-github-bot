package org.keycloak.gh.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackportScheduleRemindersTest {

    private final Instant closedAt = Instant.parse("2026-10-01T12:00:00Z");
    private BackportScheduleReminders schedule;
    private GHIssue issue;
    private Set<String> repositoryBranches = Set.of();

    @BeforeEach
    void setUp() throws IOException {
        schedule = new BackportScheduleReminders();
        schedule.pullRequests = mock(BackportPullRequests.class);
        schedule.gitHubProvider = mock(GitHubInstallationProvider.class);
        schedule.reminderIntervalDays = 1;
        schedule.mainRepository = "keycloak/keycloak";
        when(schedule.gitHubProvider.getBotLogin()).thenReturn("keycloak-bot[bot]");

        issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(123);
        when(issue.getState()).thenReturn(GHIssueState.CLOSED);
        when(issue.getStateReason()).thenReturn(GHIssueStateReason.COMPLETED);
        when(issue.getClosedAt()).thenReturn(Date.from(closedAt));
        List<GHLabel> labels = List.of(label("backport/26.6"), label("backport/26.8"), label("backport/main"), label("kind/bug"));
        when(issue.getLabels()).thenReturn(labels);
        GHUser assignee = mock(GHUser.class);
        when(assignee.getLogin()).thenReturn("alice");
        when(issue.getAssignees()).thenReturn(List.of(assignee));
        comments(List.of());
        when(schedule.pullRequests.check(issue, Set.of("release/26.6", "release/26.8")))
                .thenReturn(new BackportPullRequests.Result(true, Set.of("release/26.8")));
    }

    @Test
    void waitsFor24Hours() throws IOException {
        checkIssue(closedAt.plus(Duration.ofHours(24)).minusSeconds(1));
        verifyNoInteractions(schedule.pullRequests);
        verify(issue, never()).comment(anyString());
    }

    @Test
    void remindsAt24HoursWithOnlyMissingBranches() throws IOException {
        checkIssue(closedAt.plus(Duration.ofHours(24)));
        verify(schedule.pullRequests).check(issue, Set.of("release/26.6", "release/26.8"));
        verify(issue).comment(contains("@alice This issue is fixed in `main`, but backport PRs are still missing for: `release/26.8`."));
        verify(issue, never()).comment(contains("`release/26.6`"));
    }

    @Test
    void doesNotRemindWithoutMergedMain() throws IOException {
        when(schedule.pullRequests.check(issue, Set.of("release/26.6", "release/26.8")))
                .thenReturn(new BackportPullRequests.Result(false, Set.of("release/26.8")));
        checkIssue(closedAt.plus(Duration.ofDays(2)));
        verify(issue, never()).comment(anyString());
    }

    @Test
    void doesNotRemindWhenAllBackportsExist() throws IOException {
        when(schedule.pullRequests.check(issue, Set.of("release/26.6", "release/26.8")))
                .thenReturn(new BackportPullRequests.Result(true, Set.of()));
        checkIssue(closedAt.plus(Duration.ofDays(2)));
        verify(issue, never()).comment(anyString());
    }

    @Test
    void restartDoesNotDuplicateReminder() throws IOException {
        comments(List.of(reminder(closedAt.plus(Duration.ofDays(1)))));
        checkIssue(closedAt.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));
        verify(issue, never()).comment(anyString());
    }

    @Test
    void sendsFollowUpAfterOneDay() throws IOException {
        comments(List.of(reminder(closedAt.plus(Duration.ofDays(1)))));
        checkIssue(closedAt.plus(Duration.ofDays(2)));
        verify(issue).comment(anyString());
    }

    @Test
    void waitsAFullDayBeforeFollowUp() throws IOException {
        comments(List.of(reminder(closedAt.plus(Duration.ofDays(1)))));
        checkIssue(closedAt.plus(Duration.ofDays(2)).minusSeconds(1));
        verify(issue, never()).comment(anyString());
    }

    @Test
    void continuesAfterTwoReminders() throws IOException {
        comments(List.of(reminder(closedAt.plus(Duration.ofDays(1))), reminder(closedAt.plus(Duration.ofDays(2)))));
        checkIssue(closedAt.plus(Duration.ofDays(3)));
        verify(issue).comment(anyString());
    }

    @Test
    void usesMostRecentReminderRegardlessOfOrder() throws IOException {
        comments(List.of(reminder(closedAt.plus(Duration.ofDays(2))), reminder(closedAt.plus(Duration.ofDays(1)))));
        checkIssue(closedAt.plus(Duration.ofDays(2)).plus(Duration.ofHours(1)));
        verify(issue, never()).comment(anyString());
    }

    @Test
    void oldClosureDoesNotSuppressNewReminder() throws IOException {
        GHIssueComment old = reminder(closedAt.minus(Duration.ofDays(1)), closedAt.minusSeconds(1));
        comments(List.of(old));
        checkIssue(closedAt.plus(Duration.ofDays(1)));
        verify(issue).comment(anyString());
    }

    @Test
    void skipsReopenedIssues() throws IOException {
        when(issue.getState()).thenReturn(GHIssueState.OPEN);
        checkIssue(closedAt.plus(Duration.ofDays(1)));
        verifyNoInteractions(schedule.pullRequests);
    }

    @Test
    void skipsNotPlannedIssues() throws IOException {
        when(issue.getStateReason()).thenReturn(GHIssueStateReason.NOT_PLANNED);
        checkIssue(closedAt.plus(Duration.ofDays(1)));
        verifyNoInteractions(schedule.pullRequests);
        verify(issue, never()).getLabels();
        verify(issue, never()).comment(anyString());
    }

    @Test
    void skipsDuplicateIssues() throws IOException {
        String payload = """
                {
                    "issue": {
                        "number": 123,
                        "state": "closed",
                        "state_reason": "duplicate",
                        "closed_at": "2026-10-01T12:00:00Z",
                        "labels": [{"name": "backport/26.8"}]
                    }
                }
                """;
        GHIssue duplicate = GitHub.offline().parseEventPayload(new StringReader(payload), GHEventPayload.Issue.class).getIssue();
        schedule.checkIssue(duplicate, closedAt.plus(Duration.ofDays(1)), repositoryBranches);
        verifyNoInteractions(schedule.pullRequests);
    }

    @Test
    void skipsIssuesWithMissingResolution() throws IOException {
        when(issue.getStateReason()).thenReturn(null);
        checkIssue(closedAt.plus(Duration.ofDays(1)));
        verifyNoInteractions(schedule.pullRequests);
        verify(issue, never()).getLabels();
    }

    @Test
    void skipsIssuesWithoutBackportLabels() throws IOException {
        when(issue.getLabels()).thenReturn(List.of());
        checkIssue(closedAt.plus(Duration.ofDays(1)));
        verifyNoInteractions(schedule.pullRequests);
        verify(issue, never()).listComments();
    }

    @Test
    void skipsIssuesWithOnlyArchivedBackports() throws IOException {
        List<GHLabel> labels = List.of(label("backport/26.2"));
        when(issue.getLabels()).thenReturn(labels);
        repositoryBranches = Set.of("archive/release/26.2");

        checkIssue(closedAt.plus(Duration.ofDays(1)));

        verifyNoInteractions(schedule.pullRequests);
        verify(issue, never()).listComments();
        verify(issue, never()).comment(anyString());
    }

    @Test
    void remindsOnlyForActiveReleaseInMixedLabels() throws IOException {
        List<GHLabel> labels = List.of(label("backport/26.2"), label("backport/26.8"));
        when(issue.getLabels()).thenReturn(labels);
        repositoryBranches = Set.of("archive/release/26.2", "release/26.8");
        when(schedule.pullRequests.check(issue, Set.of("release/26.8")))
                .thenReturn(new BackportPullRequests.Result(true, Set.of("release/26.8")));

        checkIssue(closedAt.plus(Duration.ofDays(1)));

        verify(schedule.pullRequests).check(issue, Set.of("release/26.8"));
        verify(issue).comment(contains("`release/26.8`"));
        verify(issue, never()).comment(contains("`release/26.2`"));
    }

    @Test
    void archiveTakesPrecedenceOverReleaseBranch() throws IOException {
        repositoryBranches = Set.of("archive/release/26.6", "release/26.6", "archive/release/26.8", "release/26.8");

        checkIssue(closedAt.plus(Duration.ofDays(1)));

        verifyNoInteractions(schedule.pullRequests);
        verify(issue, never()).comment(anyString());
    }

    @Test
    void skipsIssueQueriesForArchivedLabels() throws IOException {
        schedule.mainRepository = "owner/test";
        GHRepository repository = mock(GHRepository.class);
        GitHub gitHub = mock(GitHub.class);
        when(schedule.gitHubProvider.getGitHubClient("owner/test")).thenReturn(gitHub);
        when(gitHub.getRepository("owner/test")).thenReturn(repository);
        GHBranch archivedBranch = mock(GHBranch.class);
        when(repository.getBranches()).thenReturn(Map.of("archive/release/26.2", archivedBranch));
        PagedIterable<GHLabel> labels = paged(List.of(label("backport/26.2")));
        when(repository.listLabels()).thenReturn(labels);

        schedule.checkBackports();

        verify(schedule.gitHubProvider).getGitHubClient("owner/test");
        verify(repository).getBranches();
        verify(repository, never()).queryIssues();
        verifyNoInteractions(schedule.pullRequests);
    }

    private void checkIssue(Instant now) throws IOException {
        schedule.checkIssue(issue, now, repositoryBranches);
    }

    private GHLabel label(String name) {
        GHLabel label = mock(GHLabel.class);
        when(label.getName()).thenReturn(name);
        return label;
    }

    private GHIssueComment reminder(Instant createdAt) throws IOException {
        return reminder(createdAt, closedAt);
    }

    private GHIssueComment reminder(Instant createdAt, Instant issueClosedAt) throws IOException {
        String payload = """
                {
                    "issue": {"number": 123},
                    "comment": {
                        "body": "<!-- backport-reminder:closed-at=%s -->",
                        "created_at": "%s",
                        "user": {"login": "keycloak-bot[bot]"}
                    }
                }
                """.formatted(issueClosedAt.toEpochMilli(), createdAt);
        return GitHub.offline().parseEventPayload(new StringReader(payload), GHEventPayload.IssueComment.class).getComment();
    }

    private void comments(List<GHIssueComment> comments) throws IOException {
        PagedIterable<GHIssueComment> iterable = paged(comments);
        when(issue.listComments()).thenReturn(iterable);
    }

    @SuppressWarnings("unchecked")
    private <T> PagedIterable<T> paged(List<T> values) {
        PagedIterable<T> iterable = mock(PagedIterable.class);
        PagedIterator<T> pagedIterator = mock(PagedIterator.class);
        Iterator<T> iterator = values.iterator();
        when(pagedIterator.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(pagedIterator.next()).thenAnswer(invocation -> iterator.next());
        when(iterable.iterator()).thenReturn(pagedIterator);
        return iterable;
    }
}
