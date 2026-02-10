package org.keycloak.gh.bot.security.email;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class GmailAdapter {

    private static final Logger LOG = Logger.getLogger(GmailAdapter.class);

    @Inject
    Gmail gmail;

    @ConfigProperty(name = "gmail.batch.size", defaultValue = "20")
    long batchSize;

    public List<Message> fetchUnreadMessages(String query) throws IOException {
        ListMessagesResponse listResponse = gmail.users().messages().list("me")
                .setQ(query)
                .setMaxResults(batchSize)
                .execute();
        return listResponse.getMessages() != null ? listResponse.getMessages() : Collections.emptyList();
    }

    public Message getMessage(String id) throws IOException {
        return gmail.users().messages().get("me", id).execute();
    }

    public Thread getThread(String threadId) throws IOException {
        return gmail.users().threads().get("me", threadId).setFormat("METADATA").execute();
    }

    public void markAsRead(String messageId) throws IOException {
        ModifyMessageRequest mods = new ModifyMessageRequest().setRemoveLabelIds(Collections.singletonList("UNREAD"));
        gmail.users().messages().modify("me", messageId, mods).execute();
    }

    public Message sendMessage(String threadId, MimeMessage email) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            email.writeTo(buffer);
        } catch (Exception e) {
            throw new IOException("Failed to serialize MimeMessage", e);
        }

        String encodedEmail = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        Message message = new Message().setRaw(encodedEmail);
        if (threadId != null) {
            message.setThreadId(threadId);
        }
        return gmail.users().messages().send("me", message).execute();
    }

    public Map<String, String> getHeadersMap(Message message) {
        if (message == null || message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return Collections.emptyMap();
        }
        return message.getPayload().getHeaders().stream()
                .collect(Collectors.toMap(
                        MessagePartHeader::getName,
                        MessagePartHeader::getValue,
                        (existing, replacement) -> existing,
                        () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
                ));
    }

    public String getHeader(Message message, String name) {
        return getHeadersMap(message).getOrDefault(name, "");
    }

    public String getBody(Message message) {
        if (message == null || message.getPayload() == null) return "";
        MessagePartBody body = message.getPayload().getBody();
        if (body != null && body.getData() != null) {
            return decode(body.getData());
        }
        return getBestContent(message.getPayload().getParts());
    }

    public record Attachment(String fileName, String mimeType, byte[] content) {}

    public List<Attachment> getAttachments(Message message) {
        List<Attachment> attachments = new ArrayList<>();
        if (message.getPayload() != null && message.getPayload().getParts() != null) {
            collectAttachments(message.getId(), message.getPayload().getParts(), attachments);
        }
        return attachments;
    }

    private void collectAttachments(String messageId, List<MessagePart> parts, List<Attachment> attachments) {
        for (MessagePart part : parts) {
            if (part.getFilename() != null && !part.getFilename().isEmpty() && part.getBody() != null && part.getBody().getAttachmentId() != null) {
                try {
                    byte[] data = fetchAttachment(messageId, part.getBody().getAttachmentId());
                    attachments.add(new Attachment(part.getFilename(), part.getMimeType(), data));
                } catch (IOException e) {
                    LOG.error("Failed to process Gmail operation", e);
                }
            }
            if (part.getParts() != null) {
                collectAttachments(messageId, part.getParts(), attachments);
            }
        }
    }

    private byte[] fetchAttachment(String messageId, String attachmentId) throws IOException {
        MessagePartBody attachPart = gmail.users().messages().attachments().get("me", messageId, attachmentId).execute();
        return Base64.getUrlDecoder().decode(attachPart.getData());
    }

    private String getBestContent(List<MessagePart> parts) {
        if (parts == null) return "";
        String htmlContent = null;
        for (MessagePart part : parts) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                if ("text/plain".equalsIgnoreCase(part.getMimeType())) {
                    return decode(part.getBody().getData());
                }
                if ("text/html".equalsIgnoreCase(part.getMimeType())) {
                    htmlContent = decode(part.getBody().getData());
                }
            }
            if (part.getParts() != null) {
                String nested = getBestContent(part.getParts());
                if (!nested.isEmpty()) return nested;
            }
        }
        return htmlContent != null ? htmlContent : "";
    }

    private String decode(String data) {
        return new String(Base64.getUrlDecoder().decode(data));
    }
}