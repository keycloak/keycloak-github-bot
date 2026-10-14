package org.keycloak.gh.bot.security.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.email.MailSender;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueCommentQueryBuilder;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RejectCommandTest {

    private MailSender mailSender;
    private RejectCommand command;
    private GHEventPayload.IssueComment payload;
    private GHIssueComment comment;
    private GHIssue issue;

    @BeforeEach
    void setUp() throws Exception {
        mailSender = mock(MailSender.class);
        command = new RejectCommand();

        setField(command, "mailSender", mailSender);
        setField(command, "targetGroup", "keycloak-security@googlegroups.com");

        payload = mock(GHEventPayload.IssueComment.class);
        comment = mock(GHIssueComment.class);
        issue = mock(GHIssue.class);

        GHRepository repository = mock(GHRepository.class);
        when(repository.getFullName()).thenReturn("keycloak/keycloak-private");
        when(payload.getRepository()).thenReturn(repository);
        when(payload.getComment()).thenReturn(comment);
        when(comment.getNodeId()).thenReturn("IC_reject123");
        when(payload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(99);
    }

    @Test
    void reject_sendsReplyAndClosesIssue() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n\nThis does not represent a valid security vulnerability.");
        setupIssueLabels(Labels.SOURCE_EMAIL, Status.TRIAGE.toLabel());
        setupIssueComments(Constants.GMAIL_THREAD_ID_PREFIX + " abc123def456");
        when(mailSender.sendReply("abc123def456", "This does not represent a valid security vulnerability.",
                "keycloak-security@googlegroups.com")).thenReturn(true);

        command.run(payload);

        verify(mailSender).sendReply("abc123def456", "This does not represent a valid security vulnerability.",
                "keycloak-security@googlegroups.com");
        verify(issue).addLabels(Status.REJECTED.toLabel());
        verify(issue).removeLabels(Status.TRIAGE.toLabel());
        verify(issue).close(GHIssueStateReason.NOT_PLANNED);
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void reject_closesWithoutEmailWhenNotFromEmail() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n\nNot a security issue.");
        setupIssueLabels(Status.TRIAGE.toLabel());

        command.run(payload);

        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
        verify(issue).addLabels(Status.REJECTED.toLabel());
        verify(issue).removeLabels(Status.TRIAGE.toLabel());
        verify(issue).close(GHIssueStateReason.NOT_PLANNED);
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void reject_skipsRemoveTriageWhenNotPresent() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n\nNot applicable.");
        setupIssueLabels("kind/bug");

        command.run(payload);

        verify(issue, never()).removeLabels(any(String[].class));
        verify(issue).addLabels(Status.REJECTED.toLabel());
        verify(issue).close(GHIssueStateReason.NOT_PLANNED);
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void reject_reactsWithThumbsDownWhenNoBody() throws Exception {
        when(comment.getBody()).thenReturn("@security reject");

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
        verify(issue, never()).addLabels(any(String[].class));
        verify(issue, never()).close(any(GHIssueStateReason.class));
    }

    @Test
    void reject_reactsWithThumbsDownWhenEmptyBody() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n   ");

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
        verify(issue, never()).addLabels(any(String[].class));
        verify(issue, never()).close(any(GHIssueStateReason.class));
    }

    @Test
    void reject_reactsWithThumbsDownWhenNoThreadId() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n\nNot a vulnerability.");
        setupIssueLabels(Labels.SOURCE_EMAIL, Status.TRIAGE.toLabel());
        setupIssueComments("Some unrelated comment without thread ID");

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
        verify(issue, never()).addLabels(any(String[].class));
        verify(issue, never()).close(any(GHIssueStateReason.class));
    }

    @Test
    void reject_reactsWithThumbsDownWhenEmailFails() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n\nRejection reasoning.");
        setupIssueLabels(Labels.SOURCE_EMAIL, Status.TRIAGE.toLabel());
        setupIssueComments(Constants.GMAIL_THREAD_ID_PREFIX + " deadbeef42");
        when(mailSender.sendReply("deadbeef42", "Rejection reasoning.",
                "keycloak-security@googlegroups.com")).thenReturn(false);

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(issue, never()).addLabels(any(String[].class));
        verify(issue, never()).close(any(GHIssueStateReason.class));
    }

    @Test
    void reject_blockedOnUnauthorizedRepository() throws Exception {
        GHRepository wrongRepo = mock(GHRepository.class);
        when(wrongRepo.getFullName()).thenReturn("keycloak/keycloak-public");
        when(payload.getRepository()).thenReturn(wrongRepo);
        when(comment.getBody()).thenReturn("@security reject\n\nRejected.");

        command.run(payload);

        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
        verify(issue, never()).addLabels(any(String[].class));
        verify(issue, never()).close(any(GHIssueStateReason.class));
        verify(comment, never()).createReaction(any());
    }

    @Test
    void reject_blocksDuplicateWebhook() throws Exception {
        when(comment.getBody()).thenReturn("@security reject\n\nDuplicate test.");
        setupIssueLabels(Status.TRIAGE.toLabel());

        CountDownLatch executeBlocked = new CountDownLatch(1);
        CountDownLatch secondCallDone = new CountDownLatch(1);

        GHLabel triageLabel = mock(GHLabel.class);
        when(triageLabel.getName()).thenReturn(Status.TRIAGE.toLabel());

        GHIssue slowIssue = mock(GHIssue.class);
        when(slowIssue.getNumber()).thenReturn(99);
        when(slowIssue.getLabels()).thenReturn(List.of(triageLabel));

        doAnswer(inv -> {
            executeBlocked.countDown();
            secondCallDone.await(5, TimeUnit.SECONDS);
            return null;
        }).when(slowIssue).addLabels(any(String[].class));

        GHEventPayload.IssueComment slowPayload = mock(GHEventPayload.IssueComment.class);
        GHRepository slowRepo = mock(GHRepository.class);
        when(slowRepo.getFullName()).thenReturn("keycloak/keycloak-private");
        when(slowPayload.getRepository()).thenReturn(slowRepo);
        when(slowPayload.getComment()).thenReturn(comment);
        when(slowPayload.getIssue()).thenReturn(slowIssue);

        RejectCommand secondCommand = new RejectCommand();
        setField(secondCommand, "mailSender", mailSender);
        setField(secondCommand, "targetGroup", "keycloak-security@googlegroups.com");

        AtomicReference<Throwable> firstThreadError = new AtomicReference<>();
        Thread firstThread = new Thread(() -> {
            try { command.run(slowPayload); } catch (Exception e) { firstThreadError.set(e); }
        });
        firstThread.start();

        executeBlocked.await(5, TimeUnit.SECONDS);
        secondCommand.run(slowPayload);
        secondCallDone.countDown();
        firstThread.join(5000);

        assertNull(firstThreadError.get(), "First thread threw an exception");
        verify(slowIssue, times(1)).addLabels(any(String[].class));
    }

    private GHLabel mockLabel(String name) {
        GHLabel label = mock(GHLabel.class);
        when(label.getName()).thenReturn(name);
        return label;
    }

    private void setupIssueLabels(String... labelNames) {
        List<GHLabel> labels = new ArrayList<>();
        for (String name : labelNames) {
            labels.add(mockLabel(name));
        }
        when(issue.getLabels()).thenReturn(labels);
    }

    @SuppressWarnings("unchecked")
    private void setupIssueComments(String... commentBodies) throws IOException {
        GHIssueCommentQueryBuilder queryBuilder = mock(GHIssueCommentQueryBuilder.class);
        when(issue.queryComments()).thenReturn(queryBuilder);

        List<GHIssueComment> comments = new ArrayList<>();
        for (String body : commentBodies) {
            GHIssueComment c = mock(GHIssueComment.class);
            when(c.getBody()).thenReturn(body);
            comments.add(c);
        }

        PagedIterable<GHIssueComment> pagedIterable = mock(PagedIterable.class);
        when(queryBuilder.list()).thenReturn(pagedIterable);

        when(pagedIterable.iterator()).thenAnswer(inv -> {
            PagedIterator<GHIssueComment> pagedIterator = mock(PagedIterator.class);
            final int[] index = {0};
            when(pagedIterator.hasNext()).thenAnswer(i -> index[0] < comments.size());
            when(pagedIterator.next()).thenAnswer(i -> comments.get(index[0]++));
            return pagedIterator;
        });
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
