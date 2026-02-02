package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.model.Message;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.security.common.Constants;
import org.keycloak.gh.bot.security.common.GitHubAdapter;
import org.keycloak.gh.bot.utils.Throttler;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class MailProcessorTest {

    @Inject MailProcessor mailProcessor;
    @InjectMock GmailAdapter gmailAdapter;
    @InjectMock GitHubAdapter githubAdapter;
    @InjectMock Throttler throttler;

    @ConfigProperty(name = "email.sender.secalert") String secAlertEmail;
    @ConfigProperty(name = "google.group.target") String targetGroup;
    @ConfigProperty(name = "gmail.user.email") String botEmail;

    @BeforeEach
    public void setup() throws IOException {
        when(githubAdapter.isAccessDenied()).thenReturn(false);
    }

    @Test
    public void testCreateNewIssueFromEmailWithEmptySubject() throws IOException {
        String threadId = "thread-empty-subject";
        Message message = createMockMessage(threadId, "", "Body", "external@user.com");

        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getBody(message)).thenReturn("Body");

        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of("From", "external@user.com", "To", targetGroup, "Subject", ""));

        when(githubAdapter.findOpenEmailIssueByThreadId(threadId)).thenReturn(Optional.empty());
        GHIssue newIssue = mock(GHIssue.class);
        when(githubAdapter.createSecurityIssue(anyString(), anyString(), anyString())).thenReturn(newIssue);

        mailProcessor.processUnreadEmails();

        verify(githubAdapter).createSecurityIssue(eq("(No Subject)"), anyString(), eq(Constants.SOURCE_EMAIL));
    }

    @Test
    public void testCreateNewIssueFromEmail() throws IOException {
        String threadId = "thread-new";
        Message message = createMockMessage(threadId, "New Vuln", "Body content", "external@user.com");

        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getBody(message)).thenReturn("Body content");
        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of("From", "external@user.com", "To", targetGroup, "Subject", "New Vuln"));

        when(githubAdapter.findOpenEmailIssueByThreadId(threadId)).thenReturn(Optional.empty());
        GHIssue newIssue = mock(GHIssue.class);
        when(githubAdapter.createSecurityIssue(anyString(), anyString(), anyString())).thenReturn(newIssue);

        mailProcessor.processUnreadEmails();

        verify(githubAdapter).createSecurityIssue(eq("New Vuln"), anyString(), eq(Constants.SOURCE_EMAIL));
        verify(githubAdapter).commentOnIssue(eq(newIssue), anyString());
    }

    @Test
    public void testAppendCommentToExistingIssue() throws IOException {
        String threadId = "thread-existing";
        Message message = createMockMessage(threadId, "Re: Vuln", "More info", "external@user.com");

        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getBody(message)).thenReturn("More info");
        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of("From", "external@user.com", "To", targetGroup));

        GHIssue existingIssue = mock(GHIssue.class);
        when(githubAdapter.findOpenEmailIssueByThreadId(threadId)).thenReturn(Optional.of(existingIssue));

        mailProcessor.processUnreadEmails();

        verify(githubAdapter, never()).createSecurityIssue(anyString(), anyString(), anyString());
        verify(githubAdapter).commentOnIssue(eq(existingIssue), anyString());
    }

    @Test
    public void testIgnoreBotMessages() throws IOException {
        Message message = createMockMessage("t1", "Sub", "Body", botEmail);
        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of("From", botEmail));

        mailProcessor.processUnreadEmails();

        verify(githubAdapter, never()).createSecurityIssue(anyString(), anyString(), anyString());
        verify(githubAdapter, never()).commentOnIssue(any(), anyString());
        verify(gmailAdapter).markAsRead(message.getId());
    }

    @Test
    public void testIgnoreInvalidGroupMessages() throws IOException {
        Message message = createMockMessage("t2", "Sub", "Body", "random@user.com");
        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of("From", "random@user.com", "To", "other@group.com"));

        mailProcessor.processUnreadEmails();

        verify(githubAdapter, never()).createSecurityIssue(anyString(), anyString(), anyString());
        verify(gmailAdapter).markAsRead(message.getId());
    }

    @Test
    public void testCveUpdatePlaceholderFromSecAlert() throws IOException {
        String threadId = "thread-123";
        String cveId = "CVE-2223-4545";
        String emailBody = "Received by our team, we assigned the " + cveId + "\n\nThanks.";

        Message message = createMockMessage(threadId, "Re: Vuln", emailBody, secAlertEmail);

        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getBody(message)).thenReturn(emailBody);

        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of(
                "From", secAlertEmail,
                "To", targetGroup,
                "Subject", "Re: Vuln"
        ));

        GHIssue existingIssue = mock(GHIssue.class);
        when(existingIssue.getTitle()).thenReturn("CVE-TBD Critical RCE");
        when(githubAdapter.findOpenEmailIssueByThreadId(threadId)).thenReturn(Optional.of(existingIssue));

        mailProcessor.processUnreadEmails();

        verify(githubAdapter).updateTitleAndLabels(existingIssue, "CVE-2223-4545 Critical RCE", Constants.KIND_CVE);
    }

    @Test
    public void testCveCorrectionFromSecAlert() throws IOException {
        String threadId = "thread-correction";
        String oldCve = "CVE-2023-0001";
        String newCve = "CVE-2023-9999";
        String emailBody = "Correction: The correct ID is " + newCve;

        Message message = createMockMessage(threadId, "Re: Vuln", emailBody, secAlertEmail);

        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getBody(message)).thenReturn(emailBody);

        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of(
                "From", secAlertEmail,
                "To", targetGroup,
                "Subject", "Re: Vuln"
        ));

        GHIssue existingIssue = mock(GHIssue.class);
        when(existingIssue.getTitle()).thenReturn(oldCve + " Critical RCE");
        when(githubAdapter.findOpenEmailIssueByThreadId(threadId)).thenReturn(Optional.of(existingIssue));

        mailProcessor.processUnreadEmails();

        verify(githubAdapter).updateTitleAndLabels(existingIssue, newCve + " Critical RCE", Constants.KIND_CVE);
    }

    @Test
    public void testNoCveUpdateIfSenderNotSecAlert() throws IOException {
        String threadId = "thread-456";
        String cveId = "CVE-2223-4545";
        String emailBody = "I think this is " + cveId;
        String randomSender = "random@user.com";

        Message message = createMockMessage(threadId, "Re: Vuln", emailBody, randomSender);

        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);
        when(gmailAdapter.getBody(message)).thenReturn(emailBody);

        when(gmailAdapter.getHeadersMap(message)).thenReturn(Map.of("From", randomSender, "To", targetGroup));

        GHIssue existingIssue = mock(GHIssue.class);
        when(existingIssue.getTitle()).thenReturn("CVE-TBD Critical RCE");
        when(githubAdapter.findOpenEmailIssueByThreadId(threadId)).thenReturn(Optional.of(existingIssue));

        mailProcessor.processUnreadEmails();

        verify(githubAdapter, never()).updateTitleAndLabels(any(), anyString(), any());
    }

    private Message createMockMessage(String threadId, String subject, String body, String from) {
        Message msg = new Message();
        msg.setId("msg-" + threadId);
        msg.setThreadId(threadId);
        return msg;
    }
}