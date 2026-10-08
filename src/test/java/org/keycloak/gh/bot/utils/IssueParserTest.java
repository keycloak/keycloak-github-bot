package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IssueParserTest {

    @Test
    public void testTokenExchange() throws IOException {
        String body = getResource("issue-body-token-exchange");

        String area = IssueParser.getAreaFromBody(body);
        assertEquals("area/token-exchange", area);
    }

    @Test
    public void testJavaCli() throws IOException {
        String body = getResource("issue-body-adapter-java-cli");

        String area = IssueParser.getAreaFromBody(body);
        assertEquals("area/adapter/java-cli", area);
    }

    @Test
    public void testDetectBugTemplate() throws IOException {
        String body = getResource("issue-body-token-exchange");

        assertEquals(IssueParser.TemplateType.BUG, IssueParser.detectTemplateType(body));
    }

    @Test
    public void testDetectEnhancementOrFeatureTemplate() throws IOException {
        String body = getResource("issue-body-enhancement");

        assertEquals(IssueParser.TemplateType.ENHANCEMENT_OR_FEATURE, IssueParser.detectTemplateType(body));
    }

    @Test
    public void testDetectNoTemplate() throws IOException {
        String body = getResource("issue-body-plain");

        assertNull(IssueParser.detectTemplateType(body));
    }

    @Test
    public void testDetectNullBody() {
        assertNull(IssueParser.detectTemplateType(null));
    }

    private String getResource(String name) throws IOException {
        try (InputStream is = IssueParserTest.class.getResourceAsStream(name)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
