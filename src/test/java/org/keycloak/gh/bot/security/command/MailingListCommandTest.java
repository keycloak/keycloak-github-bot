package org.keycloak.gh.bot.security.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.security.email.MailSender;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueCommentQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailingListCommandTest {

    private MailSender mailSender;
    private MailingListCommand command;
    private GHEventPayload.IssueComment payload;
    private GHIssueComment comment;
    private GHIssue issue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mailSender = mock(MailSender.class);
        command = new MailingListCommand();

        setField(command, "mailSender", mailSender);
        setField(command, "targetGroup", "keycloak-security@googlegroups.com");

        payload = mock(GHEventPayload.IssueComment.class);
        comment = mock(GHIssueComment.class);
        issue = mock(GHIssue.class);

        GHRepository repository = mock(GHRepository.class);
        when(repository.getFullName()).thenReturn("keycloak/keycloak-private");
        when(payload.getRepository()).thenReturn(repository);
        when(payload.getComment()).thenReturn(comment);
        when(payload.getIssue()).thenReturn(issue);
    }

    @Test
    void execute_sendsReplyAndReactsWithThumbsUp() throws Exception {
        when(comment.getBody()).thenReturn("@security reply\n\nThis is my reply to the mailing list");
        setupIssueComments("**Gmail-Thread-ID:** abc123def");
        when(mailSender.sendReply("abc123def", "This is my reply to the mailing list", "keycloak-security@googlegroups.com"))
                .thenReturn(true);

        command.run(payload);

        verify(mailSender).sendReply("abc123def", "This is my reply to the mailing list", "keycloak-security@googlegroups.com");
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @Test
    void execute_reactsWithThumbsDownWhenSendFails() throws Exception {
        when(comment.getBody()).thenReturn("@security reply\n\nFailed reply");
        setupIssueComments("**Gmail-Thread-ID:** abc123def");
        when(mailSender.sendReply("abc123def", "Failed reply", "keycloak-security@googlegroups.com"))
                .thenReturn(false);

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
    }

    @Test
    void execute_reactsWithThumbsDownWhenNoThreadIdFound() throws Exception {
        when(comment.getBody()).thenReturn("@security reply\n\nOrphan reply");
        setupIssueComments("Just a regular comment with no thread ID");

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
    }

    @Test
    void execute_reactsWithThumbsDownOnInvalidCommandSignature() throws Exception {
        when(comment.getBody()).thenReturn("@security reply extra-stuff\n\nBody text");

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
    }

    @Test
    void execute_reactsWithThumbsDownWhenNoBody() throws Exception {
        when(comment.getBody()).thenReturn("@security reply");

        command.run(payload);

        verify(comment).createReaction(ReactionContent.MINUS_ONE);
        verify(mailSender, never()).sendReply(anyString(), anyString(), anyString());
    }

    @Test
    void execute_findsThreadIdAcrossMultipleComments() throws Exception {
        when(comment.getBody()).thenReturn("@security reply\n\nMulti-comment reply");
        setupIssueComments(
                "First comment without thread ID",
                "**Gmail-Thread-ID:** deadbeef42\nSubject: CVE report\nFrom: reporter@example.com"
        );
        when(mailSender.sendReply("deadbeef42", "Multi-comment reply", "keycloak-security@googlegroups.com"))
                .thenReturn(true);

        command.run(payload);

        verify(mailSender).sendReply("deadbeef42", "Multi-comment reply", "keycloak-security@googlegroups.com");
        verify(comment).createReaction(ReactionContent.PLUS_ONE);
    }

    @SuppressWarnings("unchecked")
    private void setupIssueComments(String... commentBodies) throws IOException {
        GHIssueCommentQueryBuilder queryBuilder = mock(GHIssueCommentQueryBuilder.class);
        when(issue.queryComments()).thenReturn(queryBuilder);

        List<GHIssueComment> comments = new java.util.ArrayList<>();
        for (String body : commentBodies) {
            GHIssueComment c = mock(GHIssueComment.class);
            when(c.getBody()).thenReturn(body);
            comments.add(c);
        }

        PagedIterable<GHIssueComment> pagedIterable = mock(PagedIterable.class);
        when(queryBuilder.list()).thenReturn(pagedIterable);

        PagedIterator<GHIssueComment> pagedIterator = mock(PagedIterator.class);
        final int[] index = {0};
        when(pagedIterator.hasNext()).thenAnswer(inv -> index[0] < comments.size());
        when(pagedIterator.next()).thenAnswer(inv -> comments.get(index[0]++));
        when(pagedIterable.iterator()).thenReturn(pagedIterator);
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
