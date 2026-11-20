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
    public void testDetectLegacyEnhancementTemplate() throws IOException {
        String body = getResource("issue-body-enhancement");

        assertEquals(IssueParser.TemplateType.ENHANCEMENT, IssueParser.detectTemplateType(body));
    }

    @Test
    public void testDetectCurrentTemplates() throws IOException {
        assertEquals(IssueParser.TemplateType.BUG, IssueParser.detectTemplateType(getResource("issue-body-bug-v2")));
        assertEquals(IssueParser.TemplateType.ENHANCEMENT, IssueParser.detectTemplateType(getResource("issue-body-enhancement-v2")));
        assertEquals(IssueParser.TemplateType.FEATURE, IssueParser.detectTemplateType(getResource("issue-body-feature-v2")));
        assertEquals(IssueParser.TemplateType.TASK, IssueParser.detectTemplateType(getResource("issue-body-task-v2")));
        assertEquals("area/token-exchange", IssueParser.getAreaFromBody(getResource("issue-body-bug-v2")));
    }

    @Test
    public void testDetectIgnoresHeadingMentionedInText() {
        assertNull(IssueParser.detectTemplateType("Please add a ### Bug Description section to the docs."));
        assertNull(IssueParser.detectTemplateType("> ### Bug Description\n> quoted from another issue"));
        assertEquals(IssueParser.TemplateType.TASK,
                IssueParser.detectTemplateType("### Task Description\n\nThe template contains\n> ### Bug Description\n"));
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
