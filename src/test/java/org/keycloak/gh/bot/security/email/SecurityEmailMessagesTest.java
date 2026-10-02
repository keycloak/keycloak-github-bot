package org.keycloak.gh.bot.security.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityEmailMessagesTest {

    @Test
    void constructor_loadsPropertiesSuccessfully() {
        assertDoesNotThrow(SecurityEmailMessages::new);
    }

    @Test
    void getAcknowledgmentMessage_returnsNonEmptyText() {
        var messages = new SecurityEmailMessages();
        String ack = messages.getAcknowledgmentMessage();
        assertNotNull(ack);
        assertFalse(ack.isBlank());
    }

    @Test
    void getAcknowledgmentMessage_containsExpectedPhrases() {
        var messages = new SecurityEmailMessages();
        String ack = messages.getAcknowledgmentMessage();
        assertTrue(ack.contains("Thank you for taking the time to report this"));
        assertTrue(ack.contains("Want to be credited?"));
        assertTrue(ack.contains("Keycloak Security team"));
    }
}
