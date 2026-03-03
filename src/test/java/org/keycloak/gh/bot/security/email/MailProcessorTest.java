package org.keycloak.gh.bot.security.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MailProcessorTest {

    @ParameterizedTest
    @CsvSource({
            "'[keycloak-security] CVE report',                     'CVE report'",
            "'Re: [keycloak-security] CVE report',                 'CVE report'",
            "'Fwd: Re: [keycloak-security] CVE report',            'CVE report'",
            "'[KEYCLOAK-SECURITY] CVE report',                     'CVE report'",
            "'[some-future-list] CVE report',                      'CVE report'",
            "'Re: Fw: [anything] CVE report',                      'CVE report'",
            "'Re: CVE report',                                     'CVE report'",
            "'Fwd: CVE report',                                    'CVE report'",
            "'Re: Re: Re: CVE report',                             'CVE report'",
            "'Fw: CVE report',                                     'CVE report'",
    })
    void normalizeSubject_stripsMailingListAndReplyPrefixes(String input, String expected) {
        assertEquals(expected, MailProcessor.normalizeSubject(input));
    }

    @ParameterizedTest
    @CsvSource({
            "'CVE report',   'CVE report'",
            "'(No Subject)', '(No Subject)'",
    })
    void normalizeSubject_leavesSubjectIntactWhenNoPrefixPresent(String input, String expected) {
        assertEquals(expected, MailProcessor.normalizeSubject(input));
    }

    @Test
    void formatNewIssueComment_includesThreadIdSubjectFromAndSeparator() {
        String result = MailProcessor.formatNewIssueComment("thread-123", "CVE report", "alice@example.com", "Body text", "");
        assertEquals("**Gmail-Thread-ID:** thread-123\nSubject: CVE report\nFrom: alice@example.com\n\n---\n\nBody text", result);
    }

    @Test
    void formatNewIssueComment_appendsAttachmentSection() {
        String result = MailProcessor.formatNewIssueComment("thread-123", "CVE report", "alice@example.com", "Body text", "\n\n**Attachments:**\n- file.zip");
        assertEquals("**Gmail-Thread-ID:** thread-123\nSubject: CVE report\nFrom: alice@example.com\n\n---\n\nBody text\n\n**Attachments:**\n- file.zip", result);
    }

    @Test
    void formatReplyComment_containsOnlyFromAndSeparator() {
        String result = MailProcessor.formatReplyComment("bob@example.com", "Reply body", "");
        assertEquals("From: bob@example.com\n\n---\n\nReply body", result);
    }

}
