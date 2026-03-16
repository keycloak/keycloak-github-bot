package org.keycloak.gh.bot.security.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.email.MailSender;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueCommentQueryBuilder;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecAlertCommandTest {

    private MailSender mailSender;
    private SecAlertCommand command;
    private GHEventPayload.IssueComment payload;
    private GHIssueComment comment;
    private GHIssue issue;

    @BeforeEach
    void setUp() throws Exception {
        mailSender = mock(MailSender.class);
        command = new SecAlertCommand();

        setField(command, "mailSender", mailSender);
        setField(command, "secAlertEmail", "secalert@redhat.com");
        setField(command, "targetGroup", "keycloak-security@googlegroups.com");

        payload = mock(GHEventPayload.IssueComment.class);
        comment = mock(GHIssueComment.class);
        issue = mock(GHIssue.class);

        GHRepository repository = mock(GHRepository.class);
        when(repository.getFullName()).thenReturn("keycloak/keycloak-private");
        when(payload.getRepository()).thenReturn(repository);
        when(payload.getComment()).thenReturn(comment);
        when(comment.getNodeId()).thenReturn("IC_abc123");
        when(payload.getIssue()).thenReturn(issue);
        when(issue.getNumber()).thenReturn(42);
    }

    // --- New thread (no existing SecAlert-Thread-ID) ---

    @Test
    void newThread_sendsEmailWithSubjectAndPostsThreadId() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert CVE-2026-1234 XSS in admin console\n\nPlease triage this vulnerability.");
        setupSubject("CVE-2026-1234", "XSS", "in", "admin", "console");
        setupIssueComments("Some unrelated comment");
        setupIssueLabels(Status.TRIAGE.toLabel());
        when(issue.getTitle()).thenReturn("Wildcard Redirect URI vulnerability");
        when(mailSender.sendNewEmail("secalert@redhat.com", "keycloak-security@googlegroups.com",
                "CVE-2026-1234 XSS in admin console", "Please triage this vulnerability."))
                .thenReturn(Optional.of("19c48d1ecb33de98"));

        command.run(payload);

        verify(mailSender).sendNewEmail("secalert@redhat.com", "keycloak-security@googlegroups.com",
                "CVE-2026-1234 XSS in admin console", "Please triage this vulnerability.");
        verify(issue).comment("SecAlert email sent. " + Constants.SECALERT_THREAD_ID_PREFIX + " 19c48d1ecb33de98");
        verify(issue).removeLabels(Status.TRIAGE.toLabel());
        verify(issue).addLabels(Status.CVE_REQUEST.toLabel());
        verify(issue).setTitle("[CVE-TBD] Wildcard Redirect URI vulnerability");
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void newThread_doesNotDoublePrefixTitleWithCveTbd() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert CVE-2026-9999\n\nRetry after transient failure.");
        setupSubject("CVE-2026-9999");
        setupIssueComments();
        setupIssueLabels(Status.TRIAGE.toLabel());
        when(issue.getTitle()).thenReturn("[CVE-TBD] Already prefixed title");
        when(mailSender.sendNewEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("retrythread"));

        command.run(payload);

        verify(issue, never()).setTitle(anyString());
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void newThread_skipsRemoveTriageLabelWhenNotPresent() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert CVE-2026-5678\n\nDetails here.");
        setupSubject("CVE-2026-5678");
        setupIssueComments();
        setupIssueLabels("kind/bug");
        when(issue.getTitle()).thenReturn("Some issue title");
        when(mailSender.sendNewEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of("aabbccdd"));

        command.run(payload);

        verify(issue, never()).removeLabels(any(String[].class));
        verify(issue).addLabels(Status.CVE_REQUEST.toLabel());
        verify(issue).setTitle("[CVE-TBD] Some issue title");
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void newThread_blocksDuplicateWebhookViaInMemoryGuard() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert Race Condition\n\nBody text");
        setupSubject("Race", "Condition");
        setupIssueComments();
        setupIssueLabels(Status.TRIAGE.toLabel());

        CountDownLatch emailBlocked = new CountDownLatch(1);
        CountDownLatch secondCallDone = new CountDownLatch(1);

        when(mailSender.sendNewEmail(anyString(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            emailBlocked.countDown();
            secondCallDone.await(5, TimeUnit.SECONDS);
            return Optional.of("firstthread");
        });

        SecAlertCommand secondCommand = new SecAlertCommand();
        secondCommand.unparsedArgs = new ArrayList<>(List.of("Race", "Condition"));
        setField(secondCommand, "mailSender", mailSender);
        setField(secondCommand, "secAlertEmail", "secalert@redhat.com");
        setField(secondCommand, "targetGroup", "keycloak-security@googlegroups.com");

        Thread firstThread = new Thread(() -> {
            try { command.run(payload); } catch (IOException e) { throw new RuntimeException(e); }
        });
        firstThread.start();

        emailBlocked.await(5, TimeUnit.SECONDS);
        secondCommand.run(payload);
        secondCallDone.countDown();
        firstThread.join(5000);

        verify(mailSender, times(1)).sendNewEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void newThread_reactsWithThumbsDownWhenSendFails() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert Some Subject\n\nBody text");
        setupSubject("Some", "Subject");
        setupIssueComments();
        when(mailSender.sendNewEmail(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(issue, never()).comment(anyString());
    }

    @Test
    void newThread_reactsWithThumbsDownWhenSubjectMissing() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert\n\nBody text");
        setupIssueComments();

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendNewEmail(anyString(), anyString(), anyString(), anyString());
    }

    // --- Reply on existing thread (SecAlert-Thread-ID found) ---

    @Test
    void existingThread_repliesWithoutSubject() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert\n\nThis is a follow-up reply.");
        setupIssueComments("SecAlert email sent. " + Constants.SECALERT_THREAD_ID_PREFIX + " abc123def456");
        when(mailSender.sendThreadedEmail("abc123def456", "secalert@redhat.com",
                "keycloak-security@googlegroups.com", "This is a follow-up reply."))
                .thenReturn(true);

        command.run(payload);

        verify(mailSender).sendThreadedEmail("abc123def456", "secalert@redhat.com",
                "keycloak-security@googlegroups.com", "This is a follow-up reply.");
        verify(mailSender, never()).sendNewEmail(anyString(), anyString(), anyString(), anyString());
        verify(issue, never()).removeLabels(any(String[].class));
        verify(issue, never()).addLabels(any(String[].class));
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void existingThread_reactsWithThumbsDownWhenReplyFails() throws Exception {
        when(comment.getBody()).thenReturn("@security secalert\n\nFailed reply.");
        setupIssueComments("SecAlert email sent. " + Constants.SECALERT_THREAD_ID_PREFIX + " abc123");
        when(mailSender.sendThreadedEmail(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
    }

    @Test
    void execute_blockedOnUnauthorizedRepository() throws Exception {
        GHRepository wrongRepo = mock(GHRepository.class);
        when(wrongRepo.getFullName()).thenReturn("keycloak/keycloak-public");
        when(payload.getRepository()).thenReturn(wrongRepo);
        when(comment.getBody()).thenReturn("@security secalert Subject\n\nBody");

        command.run(payload);

        verify(mailSender, never()).sendNewEmail(anyString(), anyString(), anyString(), anyString());
        verify(mailSender, never()).sendThreadedEmail(anyString(), anyString(), anyString(), anyString());
        verify(comment, never()).createReaction(any());
    }

    private void setupSubject(String... words) {
        command.unparsedArgs = new ArrayList<>(List.of(words));
    }

    private void setupIssueLabels(String... labelNames) {
        List<GHLabel> labels = new ArrayList<>();
        for (String name : labelNames) {
            GHLabel label = mock(GHLabel.class);
            when(label.getName()).thenReturn(name);
            labels.add(label);
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
