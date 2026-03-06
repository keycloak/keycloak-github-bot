package org.keycloak.gh.bot.security.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.labels.Status;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void extractCveId_findsIdInSubject() {
        assertEquals("CVE-2026-1234", MailProcessor.extractCveId("Re: CVE-2026-1234 XSS in admin console"));
    }

    @Test
    void extractCveId_findsIdInBody() {
        assertEquals("CVE-2026-45678", MailProcessor.extractCveId("The assigned CVE ID is CVE-2026-45678. Please confirm."));
    }

    @Test
    void extractCveId_returnsNullWhenNoCvePresent() {
        assertNull(MailProcessor.extractCveId("No CVE here, just a regular message."));
    }

    @Test
    void extractCveId_returnsNullForNullInput() {
        assertNull(MailProcessor.extractCveId(null));
    }

    @Test
    void extractCveId_returnsFirstCveWhenMultiplePresent() {
        assertEquals("CVE-2026-1111", MailProcessor.extractCveId("CVE-2026-1111 and CVE-2026-2222"));
    }

    @Test
    void applyCveIdFromSecAlert_replacesTitleAndRemovesCveRequestLabel() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("[CVE-TBD] XSS in admin console");
        when(issue.getNumber()).thenReturn(42);

        GHLabel cveRequestLabel = mock(GHLabel.class);
        when(cveRequestLabel.getName()).thenReturn(Status.CVE_REQUEST.toLabel());
        when(issue.getLabels()).thenReturn(List.of(cveRequestLabel));

        MailProcessor processor = new MailProcessor();
        processor.applyCveIdFromSecAlert(issue, "Re: CVE-2026-9999 XSS in admin console", "body");

        verify(issue).setTitle("[CVE-2026-9999] XSS in admin console");
        verify(issue).removeLabels(Status.CVE_REQUEST.toLabel());
        verify(issue).addLabels(Kind.CVE.toLabel());
    }

    @Test
    void applyCveIdFromSecAlert_doesNotRemoveLabelWhenNotPresent() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("[CVE-TBD] XSS in admin console");
        when(issue.getNumber()).thenReturn(42);
        when(issue.getLabels()).thenReturn(List.of());

        MailProcessor processor = new MailProcessor();
        processor.applyCveIdFromSecAlert(issue, "Re: CVE-2026-9999 XSS in admin console", "body");

        verify(issue).setTitle("[CVE-2026-9999] XSS in admin console");
        verify(issue, never()).removeLabels(Status.CVE_REQUEST.toLabel());
        verify(issue).addLabels(Kind.CVE.toLabel());
    }

    @Test
    void applyCveIdFromSecAlert_noOpWhenTitleDoesNotStartWithCveTbd() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("[CVE-2026-1234] Already assigned");

        MailProcessor processor = new MailProcessor();
        processor.applyCveIdFromSecAlert(issue, "Re: CVE-2026-9999", "body");

        verify(issue, never()).setTitle(org.mockito.ArgumentMatchers.anyString());
        verify(issue, never()).removeLabels(org.mockito.ArgumentMatchers.any(String[].class));
        verify(issue, never()).addLabels(org.mockito.ArgumentMatchers.any(String[].class));
    }

    @Test
    void applyCveIdFromSecAlert_extractsCveFromBodyWhenNotInSubject() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("[CVE-TBD] SSRF vulnerability");
        when(issue.getNumber()).thenReturn(10);

        GHLabel cveRequestLabel = mock(GHLabel.class);
        when(cveRequestLabel.getName()).thenReturn(Status.CVE_REQUEST.toLabel());
        when(issue.getLabels()).thenReturn(List.of(cveRequestLabel));

        MailProcessor processor = new MailProcessor();
        processor.applyCveIdFromSecAlert(issue, "No CVE in subject", "Assigned CVE-2026-5555 for this issue.");

        verify(issue).setTitle("[CVE-2026-5555] SSRF vulnerability");
        verify(issue).removeLabels(Status.CVE_REQUEST.toLabel());
        verify(issue).addLabels(Kind.CVE.toLabel());
    }

    @Test
    void applyCveIdFromSecAlert_doesNotAddCveKindWhenAlreadyPresent() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("[CVE-TBD] OIDC token leak");
        when(issue.getNumber()).thenReturn(77);

        GHLabel cveKindLabel = mock(GHLabel.class);
        when(cveKindLabel.getName()).thenReturn(Kind.CVE.toLabel());
        when(issue.getLabels()).thenReturn(List.of(cveKindLabel));

        MailProcessor processor = new MailProcessor();
        processor.applyCveIdFromSecAlert(issue, "CVE-2026-8888 OIDC token leak", "body");

        verify(issue).setTitle("[CVE-2026-8888] OIDC token leak");
        verify(issue, never()).addLabels(Kind.CVE.toLabel());
    }
}
