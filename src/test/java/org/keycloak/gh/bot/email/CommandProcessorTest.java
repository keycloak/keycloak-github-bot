package org.keycloak.gh.bot.email;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.keycloak.gh.bot.email.CommandParser.Command;
import org.keycloak.gh.bot.email.CommandParser.CommandType;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueCommentQueryBuilder;
import org.kohsuke.github.GHReaction;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.ReactionContent;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(CommandProcessorTest.EmailTestProfile.class)
public class CommandProcessorTest {

    @Inject
    CommandProcessor commandProcessor;

    @InjectMock
    MailSender mailSender;

    @InjectMock
    GitHubAdapter githubAdapter;

    @InjectMock
    CommandParser commandParser;

    @ConfigProperty(name = "quarkus.application.name")
    String botName;

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    @ConfigProperty(name = "email.target.secalert")
    String secAlertEmail;

    private static final String THREAD_ID = "123456789abcdef";

    @BeforeEach
    public void setup() {
        when(commandParser.getBotName()).thenReturn(botName);
        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(Collections.emptyList());
    }

    @Test
    public void testNewSecAlertSuccess() throws IOException {
        GHIssue issue = mock(GHIssue.class);
        GHIssueComment comment = mockComment();

        when(commandParser.parse(anyString())).thenReturn(
                Optional.of(new Command(CommandType.NEW_SECALERT, Optional.of("CVE-123"), "Alert body."))
        );

        when(issue.getComments()).thenReturn(List.of(comment));

        mockQueryComments(issue, List.of(comment));

        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));
        when(mailSender.sendNewEmail(any(), any(), any(), any())).thenReturn(true);

        commandProcessor.processCommands();

        verify(mailSender).sendNewEmail(eq(secAlertEmail), eq(targetGroup), eq("CVE-123"), eq("Alert body."));
        verify(comment).createReaction(ReactionContent.EYES);
    }

    @Test
    public void testReplyKeycloakSecuritySuccess() throws IOException {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("Security Thread");
        GHIssueComment comment = mockComment();

        when(commandParser.parse(anyString())).thenReturn(
                Optional.of(new Command(CommandType.REPLY_KEYCLOAK_SECURITY, Optional.empty(), "Fixed."))
        );

        when(comment.getBody()).thenReturn("**Gmail-Thread-ID:** " + THREAD_ID);

        when(issue.getComments()).thenReturn(List.of(comment));

        mockQueryComments(issue, List.of(comment));

        when(githubAdapter.getIssuesUpdatedSince(any())).thenReturn(List.of(issue));
        when(mailSender.sendReply(any(), any(), any(), any())).thenReturn(true);

        commandProcessor.processCommands();

        verify(mailSender).sendReply(eq(THREAD_ID), eq("Security Thread"), eq("Fixed."), eq(targetGroup));
        verify(comment).createReaction(ReactionContent.EYES);
    }

    private void mockQueryComments(GHIssue issue, List<GHIssueComment> comments) throws IOException {
        GHIssueCommentQueryBuilder queryBuilder = mock(GHIssueCommentQueryBuilder.class);
        PagedIterable<GHIssueComment> pagedIterable = mock(PagedIterable.class);

        when(issue.queryComments()).thenReturn(queryBuilder);
        when(queryBuilder.since(any())).thenReturn(queryBuilder);
        when(queryBuilder.list()).thenReturn(pagedIterable);
        when(pagedIterable.toList()).thenReturn(comments);
    }

    private GHIssueComment mockComment() throws IOException {
        GHIssueComment comment = mock(GHIssueComment.class);
        when(comment.getId()).thenReturn(new Random().nextLong());
        when(comment.getBody()).thenReturn("");

        GHUser user = mock(GHUser.class);
        when(user.getLogin()).thenReturn("tester");
        when(comment.getUser()).thenReturn(user);

        PagedIterable<GHReaction> reactions = mock(PagedIterable.class);
        PagedIterator<GHReaction> iterator = mock(PagedIterator.class);
        when(iterator.hasNext()).thenReturn(false);
        when(reactions.iterator()).thenReturn(iterator);

        when(comment.listReactions()).thenReturn(reactions);

        return comment;
    }

    public static class EmailTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "GMAIL_CLIENT_ID", "test",
                    "GMAIL_CLIENT_SECRET", "test",
                    "GMAIL_REFRESH_TOKEN", "test",
                    "GMAIL_USER_EMAIL", "test@test.com",
                    "email.target.secalert", "test@test.com",
                    "google.group.target", "test@test.com",
                    "quarkus.application.name", "test-bot"
            );
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Collections.singleton(MockGitHubInstallationProvider.class);
        }
    }
}