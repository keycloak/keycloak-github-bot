package org.keycloak.gh.bot.email;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.api.services.gmail.model.Thread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A wrapper around the Google Gmail API client.
 */
@ApplicationScoped
public class GmailAdapter {

    private static final Logger LOG = Logger.getLogger(GmailAdapter.class);

    @Inject
    Gmail gmail;

    @ConfigProperty(name = "gmail.batch.size", defaultValue = "20")
    long batchSize;

    public List<Message> fetchUnreadMessages(String query) {
        try {
            ListMessagesResponse listResponse = gmail.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(batchSize)
                    .execute();
            return listResponse.getMessages() != null ? listResponse.getMessages() : Collections.emptyList();
        } catch (IOException e) {
            LOG.error("Failed to fetch messages", e);
            return Collections.emptyList();
        }
    }

    public Message getMessage(String id) {
        try {
            return gmail.users().messages().get("me", id).execute();
        } catch (IOException e) {
            LOG.errorf("Failed to get message %s", id, e);
            return null;
        }
    }

    public Thread getThread(String threadId) {
        try {
            return gmail.users().threads().get("me", threadId).setFormat("METADATA").execute();
        } catch (IOException e) {
            LOG.errorf("Failed to get thread %s", threadId, e);
            return null;
        }
    }

    public void markAsRead(String messageId) {
        try {
            ModifyMessageRequest mods = new ModifyMessageRequest().setRemoveLabelIds(Collections.singletonList("UNREAD"));
            gmail.users().messages().modify("me", messageId, mods).execute();
        } catch (IOException e) {
            LOG.errorf("Failed to mark message %s as read", messageId, e);
        }
    }

    public void sendMessage(String threadId, MimeMessage email) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());

            Message message = new Message();
            message.setRaw(encodedEmail);
            if (threadId != null) {
                message.setThreadId(threadId);
            }
            gmail.users().messages().send("me", message).execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email via Gmail API", e);
        }
    }

    public String getHeader(Message message, String name) {
        if (message == null || message.getPayload() == null || message.getPayload().getHeaders() == null) return "";
        return message.getPayload().getHeaders().stream()
                .filter(h -> h.getName().equalsIgnoreCase(name))
                .findFirst()
                .map(MessagePartHeader::getValue).orElse("");
    }

    public String getBody(Message message) {
        if (message == null || message.getPayload() == null) return "";

        MessagePartBody body = message.getPayload().getBody();
        if (body != null && body.getData() != null) {
            return new String(Base64.getUrlDecoder().decode(body.getData()));
        }

        return getPartsBody(message.getPayload().getParts()).orElse("");
    }

    private Optional<String> getPartsBody(List<MessagePart> parts) {
        if (parts == null) return Optional.empty();
        for (MessagePart part : parts) {
            if ("text/plain".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
                return Optional.of(new String(Base64.getUrlDecoder().decode(part.getBody().getData())));
            }
            if (part.getParts() != null) {
                Optional<String> result = getPartsBody(part.getParts());
                if (result.isPresent()) return result;
            }
        }
        return Optional.empty();
    }
}