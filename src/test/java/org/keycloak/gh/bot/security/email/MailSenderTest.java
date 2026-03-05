package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.Thread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailSenderTest {

    private GmailAdapter gmail;
    private MailSender mailSender;

    @BeforeEach
    void setUp() throws Exception {
        gmail = mock(GmailAdapter.class);
        mailSender = new MailSender();

        setField(mailSender, "gmail", gmail);
        setField(mailSender, "botEmail", "bot@example.com");
        mailSender.init();
    }

    @Test
    void sendReply_repliesToLastHumanSenderAndCcsMailingList() throws Exception {
        Message humanMsg = message("reporter@example.com", "msg-1", null, "Bug report");
        Thread thread = new Thread().setMessages(List.of(humanMsg));

        when(gmail.getThread("thread-1")).thenReturn(thread);
        when(gmail.getHeadersMap(humanMsg)).thenReturn(headers("reporter@example.com", "msg-1", null, "Bug report"));
        when(gmail.sendMessage(eq("thread-1"), any(MimeMessage.class))).thenReturn(new Message());

        assertTrue(mailSender.sendReply("thread-1", "Not a bug", "keycloak-security@googlegroups.com"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmail).sendMessage(eq("thread-1"), captor.capture());

        MimeMessage sent = captor.getValue();
        assertEquals("Re: Bug report", sent.getSubject());
        assertTrue(sent.getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString().contains("reporter@example.com"));
        assertTrue(sent.getRecipients(jakarta.mail.Message.RecipientType.CC)[0].toString().contains("keycloak-security@googlegroups.com"));
    }

    @Test
    void sendReply_skipsBotAndRepliesToLastHuman() throws Exception {
        Message humanMsg = message("reporter@example.com", "msg-1", null, "Report");
        Message botMsg = message("bot@example.com", "msg-2", null, "Re: Report");
        Thread thread = new Thread().setMessages(List.of(humanMsg, botMsg));

        when(gmail.getThread("thread-2")).thenReturn(thread);
        when(gmail.getHeadersMap(humanMsg)).thenReturn(headers("reporter@example.com", "msg-1", null, "Report"));
        when(gmail.getHeadersMap(botMsg)).thenReturn(headers("bot@example.com", "msg-2", null, "Re: Report"));
        when(gmail.sendMessage(eq("thread-2"), any(MimeMessage.class))).thenReturn(new Message());

        assertTrue(mailSender.sendReply("thread-2", "Reply", "group@example.com"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmail).sendMessage(eq("thread-2"), captor.capture());

        String to = captor.getValue().getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString();
        assertTrue(to.contains("reporter@example.com"));
    }

    @Test
    void sendReply_usesReplyToHeaderWhenFromIsRewrittenByGoogleGroups() throws Exception {
        Message rewrittenMsg = message("reporter@example.com", "msg-1", null, "Report");
        Thread thread = new Thread().setMessages(List.of(rewrittenMsg));

        Map<String, String> rewrittenHeaders = headers(
                "'Bruno Oliveira' via keycloak-security-lab <keycloak-security-lab@googlegroups.com>",
                "msg-1", null, "Report");
        rewrittenHeaders.put("Reply-To", "reporter@example.com");

        when(gmail.getThread("thread-groups")).thenReturn(thread);
        when(gmail.getHeadersMap(rewrittenMsg)).thenReturn(rewrittenHeaders);
        when(gmail.sendMessage(eq("thread-groups"), any(MimeMessage.class))).thenReturn(new Message());

        assertTrue(mailSender.sendReply("thread-groups", "Not a bug", "keycloak-security@googlegroups.com"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmail).sendMessage(eq("thread-groups"), captor.capture());

        String to = captor.getValue().getRecipients(jakarta.mail.Message.RecipientType.TO)[0].toString();
        assertTrue(to.contains("reporter@example.com"), "Must use Reply-To (original sender), not rewritten From (group address)");
    }

    @Test
    void sendReply_usesThreadingHeadersFromLastMessage() throws Exception {
        Message msg = message("human@example.com", "<parent@mail.com>", "<root@mail.com>", "Subject");
        Thread thread = new Thread().setMessages(List.of(msg));

        when(gmail.getThread("thread-3")).thenReturn(thread);
        when(gmail.getHeadersMap(msg)).thenReturn(headers("human@example.com", "<parent@mail.com>", "<root@mail.com>", "Subject"));
        when(gmail.sendMessage(eq("thread-3"), any(MimeMessage.class))).thenReturn(new Message());

        mailSender.sendReply("thread-3", "Body", "group@example.com");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmail).sendMessage(eq("thread-3"), captor.capture());

        MimeMessage sent = captor.getValue();
        assertEquals("<parent@mail.com>", sent.getHeader("In-Reply-To")[0]);
        assertTrue(sent.getHeader("References")[0].contains("<root@mail.com>"));
        assertTrue(sent.getHeader("References")[0].contains("<parent@mail.com>"));
    }

    @Test
    void sendReply_doesNotDuplicateRePrefix() throws Exception {
        Message msg = message("human@example.com", "m1", null, "Re: Already prefixed");
        Thread thread = new Thread().setMessages(List.of(msg));

        when(gmail.getThread("thread-4")).thenReturn(thread);
        when(gmail.getHeadersMap(msg)).thenReturn(headers("human@example.com", "m1", null, "Re: Already prefixed"));
        when(gmail.sendMessage(eq("thread-4"), any(MimeMessage.class))).thenReturn(new Message());

        mailSender.sendReply("thread-4", "Body", "group@example.com");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(gmail).sendMessage(eq("thread-4"), captor.capture());
        assertEquals("Re: Already prefixed", captor.getValue().getSubject());
    }

    @Test
    void sendReply_returnsFalseWhenThreadIsNull() throws Exception {
        when(gmail.getThread("bad")).thenReturn(null);

        assertFalse(mailSender.sendReply("bad", "Reply", "group@example.com"));
        verify(gmail, never()).sendMessage(anyString(), any(MimeMessage.class));
    }

    @Test
    void sendReply_returnsFalseOnGmailException() throws Exception {
        when(gmail.getThread("fail")).thenThrow(new IOException("API error"));

        assertFalse(mailSender.sendReply("fail", "Reply", "group@example.com"));
    }

    private Message message(String from, String messageId, String references, String subject) {
        Message msg = new Message();
        MessagePart payload = new MessagePart();
        ArrayList<MessagePartHeader> hdrs = new ArrayList<>(List.of(
                header("From", from),
                header("Message-ID", messageId),
                header("Subject", subject)
        ));
        if (references != null) hdrs.add(header("References", references));
        payload.setHeaders(hdrs);
        msg.setPayload(payload);
        return msg;
    }

    private MessagePartHeader header(String name, String value) {
        MessagePartHeader h = new MessagePartHeader();
        h.setName(name);
        h.setValue(value);
        return h;
    }

    private Map<String, String> headers(String from, String messageId, String references, String subject) {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        map.put("From", from);
        map.put("Message-ID", messageId);
        map.put("Subject", subject);
        if (references != null) map.put("References", references);
        return map;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
