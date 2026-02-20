package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.Thread;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class MailSenderTest {

    @Inject MailSender mailSender;
    @InjectMock GmailAdapter gmailAdapter;

    @BeforeEach
    public void setup() throws IOException {
        when(gmailAdapter.sendMessage(anyString(), any(MimeMessage.class))).thenReturn(new Message().setThreadId("123"));
        when(gmailAdapter.sendMessage(eq(null), any(MimeMessage.class))).thenReturn(new Message().setThreadId("123"));
    }

    @Test
    public void testSendNewEmail() throws IOException {
        String threadId = mailSender.sendNewEmail("test@example.com", null, "Subject", "Body");
        assertNotNull(threadId);
        verify(gmailAdapter).sendMessage(eq(null), any(MimeMessage.class));
    }

    @Test
    public void testSendReplyDerivesSubjectAndBody() throws IOException {
        String threadId = "thread-123";
        Thread mockThread = new Thread().setId(threadId);
        Message mockMsg = new Message().setId("msg-1");
        mockThread.setMessages(List.of(mockMsg));

        when(gmailAdapter.getThread(threadId)).thenReturn(mockThread);

        when(gmailAdapter.getHeadersMap(mockMsg)).thenReturn(Map.of(
                "Subject", "Original Subject",
                "Message-ID", "<original@example.com>",
                "From", "reporter@example.com"
        ));

        String body = "Reply Body";
        boolean success = mailSender.sendReply(threadId, body, null);

        assertTrue(success);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmailAdapter).sendMessage(eq(threadId), captor.capture());

        MimeMessage sentEmail = captor.getValue();
        try {
            assertEquals("Re: Original Subject", sentEmail.getSubject());
            assertEquals(body, sentEmail.getContent());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testSendThreadedEmailDerivesSubjectAndBody() throws IOException {
        String threadId = "thread-456";
        Thread mockThread = new Thread().setId(threadId);
        Message mockMsg = new Message().setId("msg-2");
        mockThread.setMessages(List.of(mockMsg));

        when(gmailAdapter.getThread(threadId)).thenReturn(mockThread);
        when(gmailAdapter.getHeadersMap(mockMsg)).thenReturn(Map.of("Subject", "Security Alert"));

        String body = "SecAlert Body";
        boolean success = mailSender.sendThreadedEmail(threadId, "to@redhat.com", "cc@group.com", body);

        assertTrue(success);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmailAdapter).sendMessage(eq(threadId), captor.capture());

        MimeMessage sentEmail = captor.getValue();
        try {
            assertEquals("Re: Security Alert", sentEmail.getSubject());
            assertEquals(body, sentEmail.getContent());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}