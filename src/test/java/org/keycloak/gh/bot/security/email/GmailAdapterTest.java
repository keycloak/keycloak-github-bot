package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verification for Gmail data extraction and header handling.
 */
@QuarkusTest
public class GmailAdapterTest {

    @Inject
    org.keycloak.gh.bot.security.email.GmailAdapter gmailAdapter;

    @Test
    public void testGetHeaderCaseInsensitivity() {
        Message msg = new Message();
        msg.setPayload(new MessagePart().setHeaders(List.of(
                new MessagePartHeader().setName("Subject").setValue("Critical Alert"),
                new MessagePartHeader().setName("from").setValue("security@example.com")
        )));

        assertEquals("Critical Alert", gmailAdapter.getHeader(msg, "Subject"));
        assertEquals("security@example.com", gmailAdapter.getHeader(msg, "From"));
        assertEquals("", gmailAdapter.getHeader(msg, "Non-Existent-Header"));
    }

    @Test
    public void testGetBodyDecodesBase64() {
        String originalText = "Hello World";
        String encoded = Base64.getUrlEncoder().encodeToString(originalText.getBytes());

        Message msg = new Message();
        MessagePart payload = new MessagePart();
        payload.setBody(new MessagePartBody().setData(encoded));
        msg.setPayload(payload);

        assertEquals(originalText, gmailAdapter.getBody(msg));
    }

    @Test
    public void testGetBodyHtmlFallback() {
        String html = "<html><body>Clean Content</body></html>";
        String encodedHtml = Base64.getUrlEncoder().encodeToString(html.getBytes());

        MessagePart htmlPart = new MessagePart()
                .setMimeType("text/html")
                .setBody(new MessagePartBody().setData(encodedHtml));

        Message msg = new Message();
        msg.setPayload(new MessagePart().setParts(List.of(htmlPart)));

        assertEquals(html, gmailAdapter.getBody(msg));
    }
}