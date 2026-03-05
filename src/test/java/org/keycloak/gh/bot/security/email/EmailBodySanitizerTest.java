package org.keycloak.gh.bot.security.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmailBodySanitizerTest {

    private final EmailBodySanitizer sanitizer = new EmailBodySanitizer();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\n"})
    void sanitize_returnsEmptyForBlankInput(String input) {
        assertTrue(sanitizer.sanitize(input).isEmpty());
    }

    @Test
    void sanitize_returnsPlainTextAsIs() {
        var result = sanitizer.sanitize("This is a vulnerability report.");
        assertTrue(result.isPresent());
        assertEquals("This is a vulnerability report.", result.get());
    }

    @Test
    void sanitize_stripsGoogleGroupsFooter() {
        String body = "Important content.\nYou received this message because you are subscribed to the group.";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertEquals("Important content.", result.get());
    }

    @Test
    void sanitize_stripsSignatureDivider() {
        String body = "Important content.\n-- \nJohn Doe\njohn@example.com";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertEquals("Important content.", result.get());
    }

    @Test
    void sanitize_wrapsGmailThreadHistoryInDetails() {
        String body = "New reply here.\n\nOn Mon, Jan 1, 2024 at 10:00 AM Alice wrote:\n> Original message";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("New reply here."));
        assertTrue(result.get().contains("<details>"));
        assertTrue(result.get().contains("Thread history"));
        assertTrue(result.get().contains("Original message"));
    }

    @Test
    void sanitize_wrapsOutlookPlainDividerInDetails() {
        String body = "Fresh content.\n\n-----Original Message-----\nFrom: bob@example.com";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("Fresh content."));
        assertTrue(result.get().contains("<details>"));
    }

    @Test
    void sanitize_wrapsOutlookHtmlDividerInDetails() {
        String body = "Fresh content.\n\n________________________________\nFrom: bob@example.com";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("Fresh content."));
        assertTrue(result.get().contains("<details>"));
    }

    @Test
    void sanitize_wrapsExchangeHeaderInDetails() {
        String body = "My reply.\n\nFrom: Alice Smith\nSent: Monday, January 1, 2024\nTo: Bob";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("My reply."));
        assertTrue(result.get().contains("<details>"));
    }

    @Test
    void sanitize_wrapsQuotedLinesInDetails() {
        String body = "My reply.\n\n> quoted line one\n> quoted line two";
        var result = sanitizer.sanitize(body);
        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("My reply."));
        assertTrue(result.get().contains("<details>"));
        assertTrue(result.get().contains("> quoted line one"));
    }

    @Test
    void sanitize_returnsEmptyWhenOnlySignature() {
        String body = "-- \nJohn Doe";
        assertTrue(sanitizer.sanitize(body).isEmpty());
    }
}
