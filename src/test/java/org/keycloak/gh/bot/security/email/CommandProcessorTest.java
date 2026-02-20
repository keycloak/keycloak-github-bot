package org.keycloak.gh.bot.security.email;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.security.common.CommandParser;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.common.GitHubAdapter;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHReaction;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class CommandProcessorTest {

    @Inject CommandProcessor commandProcessor;

    @InjectMock CommandParser commandParser;
    @InjectMock GitHubAdapter githubAdapter;
    @InjectMock MailSender mailSender;

    @ConfigProperty(name = "email.sender.secalert") String secAlertEmail;
    @ConfigProperty(name = "google.group.target") String targetGroup;

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1000);

    @BeforeEach
    public void setup() {
        when(githubAdapter.isAccessDenied()).thenReturn(false);
        when(commandParser.getBotName()).thenReturn("keycloak-bot");
    }

    @Test
    public void testNewSecAlertUpdatesTitleAndSendsEmail() throws IOException {
        String commandBody = "/new secalert \"Potential RCE\" Found a vuln in protocol X.";
        GHIssue issue = mockIssueWithComment(commandBody);

        CommandParser.Command cmd = new CommandParser.Command(
                CommandParser.CommandType.NEW_SECALERT,
                Optional.of("Potential RCE"),
                "Found a vuln in protocol X."
        );
        when(commandParser.parse(commandBody)).thenReturn(Optional.of(cmd));

        String generatedThreadId = "abcdef123456";
        when(mailSender.sendNewEmail(eq(secAlertEmail), eq(targetGroup), eq("Potential RCE"), anyString()))
                .thenReturn(generatedThreadId);

        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));

        commandProcessor.processCommands();

        verify(mailSender).sendNewEmail(
                eq(secAlertEmail),
                eq(targetGroup),
                eq("Potential RCE"),
                contains("Found a vuln")
        );

        verify(githubAdapter).commentOnIssue(eq(issue), contains("✅ SecAlert email sent"));
        verify(githubAdapter).commentOnIssue(eq(issue), contains(generatedThreadId));

        verify(githubAdapter).updateTitleAndLabels(eq(issue), eq(Constants.CVE_TBD_PREFIX + " Potential RCE"), eq(null));
    }

    @Test
    public void testReplyKeycloakSecuritySendsEmail() throws IOException {
        String commandBody = "/reply keycloak-security We need more info.";
        String hexThreadId = "abcdef123456";
        String botCommentBody = Constants.GMAIL_THREAD_ID_PREFIX + " " + hexThreadId;

        GHIssue issue = mockIssueWithComments(botCommentBody, commandBody);

        CommandParser.Command cmd = new CommandParser.Command(
                CommandParser.CommandType.REPLY_KEYCLOAK_SECURITY,
                Optional.empty(),
                "We need more info."
        );
        when(commandParser.parse(commandBody)).thenReturn(Optional.of(cmd));
        when(commandParser.parse(botCommentBody)).thenReturn(Optional.empty());

        when(mailSender.sendReply(anyString(), anyString(), anyString())).thenReturn(true);
        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));

        commandProcessor.processCommands();

        verify(mailSender).sendReply(
                eq(hexThreadId),
                eq("We need more info."),
                eq(targetGroup)
        );
    }

    @Test
    public void testReplySecAlertSendsEmail() throws IOException {
        String commandBody = "/reply secalert Here is the PoC.";
        String hexThreadId = "123456abcdef";
        String botCommentBody = Constants.SECALERT_THREAD_ID_PREFIX + " " + hexThreadId;

        GHIssue issue = mockIssueWithComments(botCommentBody, commandBody);

        CommandParser.Command cmd = new CommandParser.Command(
                CommandParser.CommandType.REPLY_SECALERT,
                Optional.empty(),
                "Here is the PoC."
        );
        when(commandParser.parse(commandBody)).thenReturn(Optional.of(cmd));
        when(commandParser.parse(botCommentBody)).thenReturn(Optional.empty());

        when(mailSender.sendThreadedEmail(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));

        commandProcessor.processCommands();

        verify(mailSender).sendThreadedEmail(
                eq(hexThreadId),
                eq(secAlertEmail),
                eq(targetGroup),
                eq("Here is the PoC.")
        );
    }

    @Test
    public void testUnknownCommandShowsHelp() throws IOException {
        String commandBody = "/unknown command";
        GHIssue issue = mockIssueWithComment(commandBody);

        CommandParser.Command cmd = new CommandParser.Command(
                CommandParser.CommandType.UNKNOWN,
                Optional.empty(),
                commandBody
        );
        when(commandParser.parse(commandBody)).thenReturn(Optional.of(cmd));
        when(commandParser.getHelpMessage()).thenReturn("Available commands...");

        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));

        commandProcessor.processCommands();

        verify(githubAdapter).commentOnIssue(eq(issue), contains("Available commands"));
        verify(mailSender, never()).sendNewEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testIgnoreProcessedComments() throws IOException {
        String commandBody = "/new secalert \"Subject\" Body";
        GHIssue issue = mockIssueWithComment(commandBody);

        CommandParser.Command cmd = new CommandParser.Command(
                CommandParser.CommandType.NEW_SECALERT,
                Optional.of("Subject"),
                "Body"
        );
        when(commandParser.parse(commandBody)).thenReturn(Optional.of(cmd));

        long commentId = 999999L;
        GHIssueComment comment = issue.queryComments().list().toList().get(0);
        when(comment.getId()).thenReturn(commentId);

        when(mailSender.sendNewEmail(anyString(), anyString(), anyString(), anyString())).thenReturn("abcdef123");
        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));

        commandProcessor.processCommands();
        verify(mailSender, times(1)).sendNewEmail(anyString(), anyString(), anyString(), anyString());

        commandProcessor.processCommands();
        verify(mailSender, times(1)).sendNewEmail(anyString(), anyString(), anyString(), anyString());
    }

    private GHIssue mockIssueWithComment(String body) throws IOException {
        return mockIssueWithComments(body);
    }

    private GHIssue mockIssueWithComments(String... bodies) throws IOException {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(1);

        PagedIterable<GHIssueComment> iterable = mock(PagedIterable.class);
        List<GHIssueComment> comments = new java.util.ArrayList<>();

        for (String body : bodies) {
            GHIssueComment c = mock(GHIssueComment.class);
            when(c.getBody()).thenReturn(body);
            when(c.getId()).thenReturn(ID_GENERATOR.getAndIncrement());

            GHUser user = mock(GHUser.class);
            when(user.getLogin()).thenReturn("test-user");
            when(c.getUser()).thenReturn(user);

            PagedIterable<GHReaction> reactions = mock(PagedIterable.class);
            PagedIterator<GHReaction> iterator = mock(PagedIterator.class);
            when(iterator.hasNext()).thenReturn(false);

            when(reactions.iterator()).thenReturn(iterator);
            when(reactions.toList()).thenReturn(Collections.emptyList());
            when(c.listReactions()).thenReturn(reactions);

            comments.add(c);
        }

        when(iterable.toList()).thenReturn(comments);
        when(iterable.iterator()).thenAnswer(i -> comments.iterator());

        org.kohsuke.github.GHIssueCommentQueryBuilder queryBuilder = mock(org.kohsuke.github.GHIssueCommentQueryBuilder.class);
        when(issue.queryComments()).thenReturn(queryBuilder);
        when(queryBuilder.since(any())).thenReturn(queryBuilder);
        when(queryBuilder.list()).thenReturn(iterable);

        return issue;
    }
}