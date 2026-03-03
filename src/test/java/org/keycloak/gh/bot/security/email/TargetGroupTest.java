package org.keycloak.gh.bot.security.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TargetGroupTest {

    @Test
    void from_parsesGoogleGroupsEmail() {
        var group = TargetGroup.from("keycloak-security@googlegroups.com");
        assertEquals("keycloak-security@googlegroups.com", group.email());
        assertEquals("keycloak-security", group.id());
        assertEquals("googlegroups.com", group.domain());
    }

    @Test
    void from_parsesCustomDomainEmail() {
        var group = TargetGroup.from("security@keycloak.org");
        assertEquals("security@keycloak.org", group.email());
        assertEquals("security", group.id());
        assertEquals("keycloak.org", group.domain());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid-no-at-sign", "missing-at"})
    void from_rejectsInvalidEmails(String input) {
        assertThrows(IllegalArgumentException.class, () -> TargetGroup.from(input));
    }

    @Test
    void getArchiveLink_generatesGoogleGroupsUrl() {
        var group = TargetGroup.from("keycloak-security@googlegroups.com");
        String link = group.getArchiveLink("abc123@mail.gmail.com");
        assertTrue(link.startsWith("https://groups.google.com/g/keycloak-security/search?q="));
        assertTrue(link.contains("rfc822msgid"));
        assertTrue(link.contains("abc123"));
    }

    @Test
    void getArchiveLink_includesDomainPathForCustomDomains() {
        var group = TargetGroup.from("security@keycloak.org");
        String link = group.getArchiveLink("msg-id-1");
        assertTrue(link.startsWith("https://groups.google.com/a/keycloak.org/g/security/search?q="));
    }

    @Test
    void matchesListId_returnsTrueWhenHeaderContainsGroupId() {
        var group = TargetGroup.from("keycloak-security@googlegroups.com");
        assertTrue(group.matchesListId("<keycloak-security.googlegroups.com>"));
    }

    @Test
    void matchesListId_returnsFalseForUnrelatedListId() {
        var group = TargetGroup.from("keycloak-security@googlegroups.com");
        assertFalse(group.matchesListId("<other-list.googlegroups.com>"));
    }

}
