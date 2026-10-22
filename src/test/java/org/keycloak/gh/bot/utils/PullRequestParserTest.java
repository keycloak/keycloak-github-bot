package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PullRequestParserTest {

    @ParameterizedTest
    @CsvSource({"Close #123,123", "Closes #123,123", "Closed #123,123", "Fix #123,123",
            "FIXES: #123,123", "Fixed #123,123", "Resolve #123,123", "Resolves #123,123",
            "Resolved #123,123", "Closes #1234,1234", "Fixes #2147483647,2147483647",
            "Closes keycloak/keycloak#123,123", "Fixes KEYCLOAK/Keycloak#123,123",
            "Closes: https://github.com/keycloak/keycloak/issues/53957,53957",
            "Closes https://github.com/keycloak/keycloak/issues/53210.,53210"})
    void parsesClosingReferences(String body, int number) {
        assertEquals(Set.of(number), PullRequestParser.closingIssues(body, "keycloak/keycloak"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Related to #123", "Closes other/repo#123",
            "Closes https://github.com/keycloak/keycloak/pull/123",
            "Fixes https://example.com/keycloak/keycloak/issues/123",
            "Fixes https://github.com/other/repo/issues/123", "Closes #0", "Closes #-123",
            "Closes #999999999999999999999", "Closes #2147483648", "Closes #123abc",
            "Closes #123/456", "Discloses #123"})
    void ignoresUnsupportedReferences(String body) {
        assertEquals(Set.of(), PullRequestParser.closingIssues(body, "keycloak/keycloak"));
    }

    @Test
    void deduplicatesReferences() {
        assertEquals(Set.of(123, 456), PullRequestParser.closingIssues(
                "Closes #123\nFixes #456\nResolves keycloak/keycloak#123\n"
                        + "Closes https://github.com/keycloak/keycloak/issues/456", "keycloak/keycloak"));
    }

    @Test
    void parsesLocalReferencesAlongsideUnsupportedReferences() {
        assertEquals(Set.of(123, 456), PullRequestParser.closingIssues(
                "Closes other/repo#789\nFixes #123\nCloses #999999999999999999999\nResolves #456",
                "keycloak/keycloak"));
    }

    @Test
    void usesCurrentRepositoryRatherThanHardcodedRepository() {
        assertEquals(Set.of(123, 456), PullRequestParser.closingIssues(
                "Closes other/repo#123\nFixes https://github.com/other/repo/issues/456\n"
                        + "Closes keycloak/keycloak#789\nFixes https://github.com/keycloak/keycloak/issues/789",
                "other/repo"));
    }
}
