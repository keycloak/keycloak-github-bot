package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IssueParserTest {

    @Test
    public void testTokenExchange() throws IOException {
        InputStream is = IssueParserTest.class.getResourceAsStream("issue-body-token-exchange");
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        String area = IssueParser.getAreaFromBody(body);
        assertEquals("area/token-exchange", area);
    }

    @Test
    public void testJavaCli() throws IOException {
        InputStream is = IssueParserTest.class.getResourceAsStream("issue-body-adapter-java-cli");
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        String area = IssueParser.getAreaFromBody(body);
        assertEquals("area/adapter/java-cli", area);
    }

}
