package org.keycloak.gh.bot.email;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class IncomingMailProcessorTest {

    @Inject
    IncomingMailProcessor incomingMailProcessor;

    @InjectMock
    GmailAdapter gmailAdapter;

    @InjectMock
    GitHubAdapter githubAdapter;

    @ConfigProperty(name = "google.group.target")
    String targetGroup;

    private static final String THREAD_ID = "123456789abcdef";

    @BeforeEach
    public void setup() {
        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(Collections.emptyList());

        when(gmailAdapter.getHeader(any(), anyString())).thenCallRealMethod();
        when(gmailAdapter.getBody(any())).thenCallRealMethod();
    }

    @Test
    public void testNewThreadCreatesIssue() throws IOException {
        Message message = createMockMessage(THREAD_ID, "Vulnerability", "Body content", "user@test.com");
        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);

        when(githubAdapter.findIssueByThreadId(THREAD_ID)).thenReturn(Optional.empty());

        GHIssue mockIssue = mock(GHIssue.class);
        when(mockIssue.getNumber()).thenReturn(101);
        when(githubAdapter.createIssue(anyString(), anyString())).thenReturn(mockIssue);

        incomingMailProcessor.processUnreadEmails();

        verify(githubAdapter).createIssue(eq("Vulnerability"), anyString());
        verify(mockIssue).addLabels(Labels.STATUS_TRIAGE);
        verify(gmailAdapter).markAsRead(message.getId());
    }

    @Test
    public void testReplyAppendsComment() throws IOException {
        Message message = createMockMessage(THREAD_ID, "Re: New Vuln", "More details here.", "user@test.com");
        when(gmailAdapter.fetchUnreadMessages(anyString())).thenReturn(List.of(message));
        when(gmailAdapter.getMessage(message.getId())).thenReturn(message);

        GHIssue existingIssue = mock(GHIssue.class);

        when(githubAdapter.findIssueByThreadId(THREAD_ID)).thenReturn(Optional.of(existingIssue));

        incomingMailProcessor.processUnreadEmails();

        verify(githubAdapter).commentOnIssue(eq(existingIssue), contains("More details here."));
        verify(githubAdapter, never()).createIssue(anyString(), anyString());
        verify(gmailAdapter).markAsRead(message.getId());
    }

    private Message createMockMessage(String threadId, String subject, String body, String from) {
        Message msg = new Message();
        msg.setId(UUID.randomUUID().toString());
        msg.setThreadId(threadId);

        MessagePart payload = new MessagePart();
        List<MessagePartHeader> headers = new ArrayList<>();
        headers.add(createHeader("Subject", subject));
        headers.add(createHeader("From", from));

        headers.add(createHeader("To", targetGroup));

        String listId = targetGroup.replace("@", ".");
        headers.add(createHeader("List-ID", "<" + listId + ">"));

        payload.setHeaders(headers);
        payload.setMimeType("text/plain");

        MessagePartBody partBody = new MessagePartBody();
        partBody.setData(Base64.getUrlEncoder().encodeToString(body.getBytes()));
        payload.setBody(partBody);
        msg.setPayload(payload);
        return msg;
    }

    private MessagePartHeader createHeader(String name, String value) {
        MessagePartHeader h = new MessagePartHeader();
        h.setName(name);
        h.setValue(value);
        return h;
    }
}