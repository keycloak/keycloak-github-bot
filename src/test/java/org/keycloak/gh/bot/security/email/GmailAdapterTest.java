package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GmailAdapterTest {

    private final GmailAdapter adapter = new GmailAdapter();

    @Test
    void getHeadersMap_extractsHeadersFromMessage() {
        var msg = messageWithHeaders(
                header("From", "alice@example.com"),
                header("Subject", "CVE report"),
                header("List-ID", "<keycloak-security.googlegroups.com>")
        );

        var headers = adapter.getHeadersMap(msg);
        assertEquals("alice@example.com", headers.get("From"));
        assertEquals("CVE report", headers.get("Subject"));
    }

    @Test
    void getHeadersMap_isCaseInsensitive() {
        var msg = messageWithHeaders(header("From", "alice@example.com"));
        var headers = adapter.getHeadersMap(msg);
        assertEquals("alice@example.com", headers.get("from"));
        assertEquals("alice@example.com", headers.get("FROM"));
    }

    @Test
    void getHeadersMap_keepsFirstValueOnDuplicateHeaders() {
        var msg = messageWithHeaders(
                header("From", "first@example.com"),
                header("From", "second@example.com")
        );
        assertEquals("first@example.com", adapter.getHeadersMap(msg).get("From"));
    }

    @Test
    void getBody_extractsDirectPayloadBody() {
        var msg = new Message().setPayload(
                new MessagePart().setBody(new MessagePartBody().setData(encode("Hello world")))
        );
        assertEquals("Hello world", adapter.getBody(msg));
    }

    @Test
    void getBody_prefersPlainTextOverHtml() {
        var htmlPart = part("text/html", "<b>Hello</b>");
        var plainPart = part("text/plain", "Hello plain");

        var msg = new Message().setPayload(
                new MessagePart().setParts(List.of(htmlPart, plainPart))
        );
        assertEquals("Hello plain", adapter.getBody(msg));
    }

    @Test
    void getBody_fallsBackToHtmlWhenNoPlainText() {
        var htmlPart = part("text/html", "<b>Hello</b>");

        var msg = new Message().setPayload(
                new MessagePart().setParts(List.of(htmlPart))
        );
        assertEquals("<b>Hello</b>", adapter.getBody(msg));
    }

    @Test
    void getBody_extractsFromNestedParts() {
        var nested = part("text/plain", "Nested content");
        var wrapper = new MessagePart().setMimeType("multipart/alternative").setParts(List.of(nested));

        var msg = new Message().setPayload(new MessagePart().setParts(List.of(wrapper)));
        assertEquals("Nested content", adapter.getBody(msg));
    }

    @Test
    void getAttachments_collectsAttachmentsWithIds() {
        var attachment = new MessagePart()
                .setFilename("report.pdf")
                .setMimeType("application/pdf")
                .setBody(new MessagePartBody().setAttachmentId("att-1"));
        var textPart = part("text/plain", "body");

        var msg = new Message().setPayload(new MessagePart().setParts(List.of(textPart, attachment)));
        var result = adapter.getAttachments(msg);

        assertEquals(1, result.size());
        assertEquals("report.pdf", result.get(0).fileName());
        assertEquals("application/pdf", result.get(0).mimeType());
    }

    @Test
    void getAttachments_ignoresPartsWithoutAttachmentId() {
        var inline = new MessagePart()
                .setFilename("image.png")
                .setMimeType("image/png")
                .setBody(new MessagePartBody());

        var msg = new Message().setPayload(new MessagePart().setParts(List.of(inline)));
        assertTrue(adapter.getAttachments(msg).isEmpty());
    }

    @Test
    void getAttachments_ignoresPartsWithBlankFilename() {
        var noName = new MessagePart()
                .setFilename("")
                .setMimeType("application/octet-stream")
                .setBody(new MessagePartBody().setAttachmentId("att-1"));

        var msg = new Message().setPayload(new MessagePart().setParts(List.of(noName)));
        assertTrue(adapter.getAttachments(msg).isEmpty());
    }

    @Test
    void getAttachments_collectsNestedAttachments() {
        var deepAttachment = new MessagePart()
                .setFilename("deep.zip")
                .setMimeType("application/zip")
                .setBody(new MessagePartBody().setAttachmentId("att-deep"));
        var wrapper = new MessagePart().setMimeType("multipart/mixed").setParts(List.of(deepAttachment));

        var msg = new Message().setPayload(new MessagePart().setParts(List.of(wrapper)));
        assertEquals(1, adapter.getAttachments(msg).size());
        assertEquals("deep.zip", adapter.getAttachments(msg).get(0).fileName());
    }

    private static Message messageWithHeaders(MessagePartHeader... headers) {
        return new Message().setPayload(new MessagePart().setHeaders(List.of(headers)));
    }

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static MessagePart part(String mimeType, String content) {
        return new MessagePart()
                .setMimeType(mimeType)
                .setBody(new MessagePartBody().setData(encode(content)));
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
