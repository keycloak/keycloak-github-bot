package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class GmailAdapter {

    @Inject
    Gmail gmail;

    @ConfigProperty(name = "gmail.batch.size", defaultValue = "20")
    long batchSize;

    public List<Message> fetchUnreadMessages(String query) throws IOException {
        var response = gmail.users().messages().list("me").setQ(query).setMaxResults(batchSize).execute();
        var messages = response.getMessages();
        return messages == null ? List.of() : messages;
    }

    public Message getMessage(String id) throws IOException {
        return gmail.users().messages().get("me", id).execute();
    }

    public void markAsRead(String messageId) throws IOException {
        var mods = new ModifyMessageRequest().setRemoveLabelIds(List.of("UNREAD"));
        gmail.users().messages().modify("me", messageId, mods).execute();
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

    public String getBody(Message message) {
        if (message == null || message.getPayload() == null) return "";
        var body = message.getPayload().getBody();
        if (body != null && body.getData() != null) {
            return decode(body.getData());
        }
        return getBestContent(message.getPayload().getParts());
    }

    public List<Attachment> getAttachments(Message message) {
        if (message == null || message.getPayload() == null || message.getPayload().getParts() == null) {
            return List.of();
        }
        List<Attachment> attachments = new ArrayList<>();
        collectAttachments(message.getPayload().getParts(), attachments);
        return attachments;
    }

    private void collectAttachments(List<MessagePart> parts, List<Attachment> attachments) {
        for (var part : parts) {
            if (isAttachment(part)) {
                attachments.add(new Attachment(part.getFilename(), part.getMimeType()));
            }
            if (part.getParts() != null) {
                collectAttachments(part.getParts(), attachments);
            }
        }
    }

    private boolean isAttachment(MessagePart part) {
        return part.getFilename() != null && !part.getFilename().isBlank()
                && part.getBody() != null && part.getBody().getAttachmentId() != null;
    }

    private String getBestContent(List<MessagePart> parts) {
        if (parts == null) return "";
        String htmlContent = null;
        for (var part : parts) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                if ("text/plain".equalsIgnoreCase(part.getMimeType())) {
                    return decode(part.getBody().getData());
                }
                if ("text/html".equalsIgnoreCase(part.getMimeType())) {
                    htmlContent = decode(part.getBody().getData());
                }
            }
            if (part.getParts() != null) {
                var nested = getBestContent(part.getParts());
                if (!nested.isEmpty()) return nested;
            }
        }
        return htmlContent != null ? htmlContent : "";
    }

    private String decode(String data) {
        return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }

    public record Attachment(String fileName, String mimeType) {
    }
}