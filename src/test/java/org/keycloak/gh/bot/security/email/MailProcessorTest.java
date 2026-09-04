package org.keycloak.gh.bot.security.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.security.common.Constants;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueCommentQueryBuilder;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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

    // --- extractAllCveIds ---

    @Test
    void extractAllCveIds_findsMultipleCves() {
        List<String> result = MailProcessor.extractAllCveIds("CVE-2026-9083 and CVE-2026-19729");
        assertEquals(List.of("CVE-2026-9083", "CVE-2026-19729"), result);
    }

    @Test
    void extractAllCveIds_returnsEmptyForNull() {
        assertTrue(MailProcessor.extractAllCveIds(null).isEmpty());
    }

    @Test
    void extractAllCveIds_returnsEmptyWhenNoCve() {
        assertTrue(MailProcessor.extractAllCveIds("No CVE here").isEmpty());
    }

    // --- selectLatestCveId ---

    @Test
    void selectLatestCveId_picksHighestFromSubjectAndBody() {
        assertEquals("CVE-2026-19729",
                MailProcessor.selectLatestCveId("Incomplete fix for CVE-2026-9083", "*Reference : CVE-2026-19729"));
    }

    @Test
    void selectLatestCveId_picksHighestWhenMultipleInBody() {
        assertEquals("CVE-2026-19729",
                MailProcessor.selectLatestCveId(null, "Report about CVE-2026-9083. Assigned CVE-2026-19729."));
    }

    @Test
    void selectLatestCveId_fallsBackToSubjectWhenBodyEmpty() {
        assertEquals("CVE-2026-9083",
                MailProcessor.selectLatestCveId("Fix for CVE-2026-9083", "No CVE in body"));
    }

    @Test
    void selectLatestCveId_returnsNullWhenNoCveAnywhere() {
        assertNull(MailProcessor.selectLatestCveId("No CVE", "Also no CVE"));
    }

    @Test
    void selectLatestCveId_returnsNullWhenBothInputsNull() {
        assertNull(MailProcessor.selectLatestCveId(null, null));
    }

    @Test
    void selectLatestCveId_returnsSingleCveWhenSameInBoth() {
        assertEquals("CVE-2026-9083",
                MailProcessor.selectLatestCveId("CVE-2026-9083", "Reference: CVE-2026-9083"));
    }

    @Test
    void selectLatestCveId_comparesAcrossYears() {
        assertEquals("CVE-2026-1",
                MailProcessor.selectLatestCveId("CVE-2025-999999", "CVE-2026-1"));
    }

    // --- applyCveIdFromSecAlert bug scenario ---

    @Test
    void applyCveIdFromSecAlert_picksNewCveOverOldInSubject() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getTitle()).thenReturn("[CVE-TBD] Incomplete fix for CVE-2026-9083");
        when(issue.getNumber()).thenReturn(992);

        GHLabel cveRequestLabel = mock(GHLabel.class);
        when(cveRequestLabel.getName()).thenReturn(Status.CVE_REQUEST.toLabel());
        when(issue.getLabels()).thenReturn(List.of(cveRequestLabel));

        MailProcessor processor = new MailProcessor();
        processor.applyCveIdFromSecAlert(issue, "Incomplete fix for CVE-2026-9083 - #GHI-992",
                "*Reference : CVE-2026-19729\n*Embargo status : Public");

        verify(issue).setTitle("[CVE-2026-19729] Incomplete fix for CVE-2026-9083");
        verify(issue).removeLabels(Status.CVE_REQUEST.toLabel());
        verify(issue).addLabels(Kind.CVE.toLabel());
    }

    // --- resolveIssueByGhiTag ---

    @Test
    void resolveIssueByGhiTag_resolvesIssueFromSubjectTag() throws Exception {
        GHRepository repository = mock(GHRepository.class);
        GHIssue issue = mock(GHIssue.class);
        when(repository.getIssue(42)).thenReturn(issue);

        MailProcessor processor = new MailProcessor();
        Optional<GHIssue> result = processor.resolveIssueByGhiTag(repository, "Re: CVE-2026-1234 XSS - #GHI-42");

        assertTrue(result.isPresent());
        assertEquals(issue, result.get());
    }

    @Test
    void resolveIssueByGhiTag_returnsEmptyWhenNoTagPresent() {
        GHRepository repository = mock(GHRepository.class);

        MailProcessor processor = new MailProcessor();
        Optional<GHIssue> result = processor.resolveIssueByGhiTag(repository, "Re: CVE-2026-1234 XSS in admin console");

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveIssueByGhiTag_returnsEmptyForNullSubject() {
        GHRepository repository = mock(GHRepository.class);

        MailProcessor processor = new MailProcessor();
        Optional<GHIssue> result = processor.resolveIssueByGhiTag(repository, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveIssueByGhiTag_returnsEmptyWhenIssueFetchFails() throws Exception {
        GHRepository repository = mock(GHRepository.class);
        when(repository.getIssue(99)).thenThrow(new IOException("not found"));

        MailProcessor processor = new MailProcessor();
        Optional<GHIssue> result = processor.resolveIssueByGhiTag(repository, "Subject - #GHI-99");

        assertTrue(result.isEmpty());
    }

    // --- recordSecAlertThreadIdIfMissing ---

    @Test
    void recordSecAlertThreadIdIfMissing_postsMarkerWhenAbsent() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(10);
        stubIssueComments(issue, "Some unrelated comment");

        MailProcessor processor = new MailProcessor();
        processor.recordSecAlertThreadIdIfMissing(issue, "deadbeef123");

        verify(issue).comment(Constants.SECALERT_THREAD_ID_PREFIX + " deadbeef123");
    }

    @Test
    void recordSecAlertThreadIdIfMissing_skipsWhenMarkerAlreadyExists() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(10);
        stubIssueComments(issue, Constants.SECALERT_THREAD_ID_PREFIX + " existingthread");

        MailProcessor processor = new MailProcessor();
        processor.recordSecAlertThreadIdIfMissing(issue, "newthread");

        verify(issue, never()).comment(anyString());
    }

    @Test
    void recordSecAlertThreadIdIfMissing_skipsOnBlankThreadId() throws Exception {
        GHIssue issue = mock(GHIssue.class);

        MailProcessor processor = new MailProcessor();
        processor.recordSecAlertThreadIdIfMissing(issue, "");

        verify(issue, never()).comment(anyString());
        verify(issue, never()).queryComments();
    }

    @Test
    void recordSecAlertThreadIdIfMissing_usesCache() throws Exception {
        GHIssue issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(10);
        stubIssueComments(issue, Constants.SECALERT_THREAD_ID_PREFIX + " firstthread");

        MailProcessor processor = new MailProcessor();
        processor.recordSecAlertThreadIdIfMissing(issue, "secondthread");
        processor.recordSecAlertThreadIdIfMissing(issue, "thirdthread");

        verify(issue.queryComments(), org.mockito.Mockito.times(1)).list();
    }

    // --- isPsirtClosureNotification ---

    @Test
    void isPsirtClosureNotification_detectsRealClosureBody() {
        String body = "Your request PSIRTSUPT-21634: Incomplete fix for CVE-2026-9083 - #GHI-992 has been resolved.\n\n"
                + "This request is now closed. If you have a new security concern, please reach out.";
        assertTrue(MailProcessor.isPsirtClosureNotification(body));
    }

    @Test
    void isPsirtClosureNotification_detectsDifferentProjectKey() {
        String body = "Your request SECVULN-1234: Some issue has been resolved.\n\n"
                + "This request is now closed.";
        assertTrue(MailProcessor.isPsirtClosureNotification(body));
    }

    @Test
    void isPsirtClosureNotification_returnsFalseForCveAssignment() {
        String body = "The assigned CVE ID is CVE-2026-9999. Please confirm the details.";
        assertFalse(MailProcessor.isPsirtClosureNotification(body));
    }

    @Test
    void isPsirtClosureNotification_returnsFalseForTicketRefOnly() {
        String body = "Your request PSIRTSUPT-21634 has been updated with new information.";
        assertFalse(MailProcessor.isPsirtClosureNotification(body));
    }

    @Test
    void isPsirtClosureNotification_returnsFalseForClosurePhraseOnly() {
        String body = "This request is now closed. Thank you.";
        assertFalse(MailProcessor.isPsirtClosureNotification(body));
    }

    @Test
    void isPsirtClosureNotification_returnsFalseForNullBody() {
        assertFalse(MailProcessor.isPsirtClosureNotification(null));
    }

    @Test
    void isPsirtClosureNotification_returnsFalseForBlankBody() {
        assertFalse(MailProcessor.isPsirtClosureNotification(""));
    }

    @SuppressWarnings("unchecked")
    private void stubIssueComments(GHIssue issue, String... commentBodies) throws IOException {
        GHIssueCommentQueryBuilder queryBuilder = mock(GHIssueCommentQueryBuilder.class);
        when(issue.queryComments()).thenReturn(queryBuilder);

        List<GHIssueComment> comments = new ArrayList<>();
        for (String body : commentBodies) {
            GHIssueComment c = mock(GHIssueComment.class);
            when(c.getBody()).thenReturn(body);
            comments.add(c);
        }

        PagedIterable<GHIssueComment> pagedIterable = mock(PagedIterable.class);
        when(queryBuilder.list()).thenReturn(pagedIterable);

        when(pagedIterable.iterator()).thenAnswer(inv -> {
            PagedIterator<GHIssueComment> pagedIterator = mock(PagedIterator.class);
            final int[] index = {0};
            when(pagedIterator.hasNext()).thenAnswer(i -> index[0] < comments.size());
            when(pagedIterator.next()).thenAnswer(i -> comments.get(index[0]++));
            return pagedIterator;
        });
    }
}
