package org.keycloak.gh.bot.security.jira;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JiraIssueParserTest {

    private final org.keycloak.gh.bot.security.jira.JiraIssueParser parser = new org.keycloak.gh.bot.security.jira.JiraIssueParser();

    @Test
    public void testParseTitle_StandardFormat() {
        String input = "CVE-2023-1234 rhbk/keycloak: Keycloak: Unauthorized access via improper validation";
        String expected = "CVE-2023-1234 Unauthorized access via improper validation";
        assertEquals(expected, parser.parseTitle(input));
    }

    @Test
    public void testParseTitle_WithEmbargoAndSuffix() {
        String input = "EMBARGOED CVE-2023-9999 Keycloak: RCE in Admin Console [rhbk-26.4]";
        String expected = "CVE-2023-9999 RCE in Admin Console";
        assertEquals(expected, parser.parseTitle(input));
    }

    @Test
    public void testParseTitle_ComplexPrefixes() {
        String input = "CVE-2024-0001 rhbk/keycloak-rhel9: Keycloak: SAML: Fix signature validation";
        String expected = "CVE-2024-0001 Fix signature validation";
        assertEquals(expected, parser.parseTitle(input));
    }

    @Test
    public void testParseTitle_SafeguardEmptyResult() {
        String input = "CVE-2025-1111 Keycloak:";
        String expected = "CVE-2025-1111 Keycloak:";
        assertEquals(expected, parser.parseTitle(input));
    }

    @Test
    public void testParseDescription_ExtractsFlawOnly() {
        String input = """
                Security Tracking Issue
                Do not make this public.
                
                Flaw:
                This is the actual description of the vulnerability.
                It spans multiple lines.
                
                Statement:
                This is the impact statement.
                
                References:
                https://redhat.com
                """;

        String expected = """
                This is the actual description of the vulnerability.
                It spans multiple lines.
                """.trim();

        assertEquals(expected, parser.parseDescription(input));
    }

    @Test
    public void testParseDescription_SkipsRepeatedTitle() {
        String input = """
                Flaw:
                Keycloak: Unauthorized access via improper validation
                This is the real content that should be kept.
                
                Mitigation:
                Upgrade.
                """;

        String expected = "This is the real content that should be kept.";
        assertEquals(expected, parser.parseDescription(input));
    }

    @Test
    public void testParseDescription_NoMarkers() {
        String input = "Just a plain description without markers.";
        assertEquals("", parser.parseDescription(input));
    }
}