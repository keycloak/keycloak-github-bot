package org.keycloak.gh.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AddAreaLabelToBugsTest {

    private GHEventPayload.Issue issuePayload;
    private GHIssue issue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() throws IOException {
        issuePayload = mock(GHEventPayload.Issue.class);
        issue = mock(GHIssue.class);
        GHRepository repository = mock(GHRepository.class);

        when(issuePayload.getIssue()).thenReturn(issue);
        when(issue.getRepository()).thenReturn(repository);
        when(issue.getHtmlUrl()).thenReturn(new URL("https://github.com/keycloak/keycloak/issues/1"));

        PagedIterable<GHLabel> labelIterable = mock(PagedIterable.class);
        when(labelIterable.withPageSize(anyInt())).thenReturn(labelIterable);
        PagedIterator<GHLabel> labelIterator = mock(PagedIterator.class);
        when(labelIterable.iterator()).thenReturn(labelIterator);

        GHLabel kindBugRepoLabel = mockLabel("kind/bug");
        GHLabel kindEnhancementRepoLabel = mockLabel("kind/enhancement");
        GHLabel statusTriageRepoLabel = mockLabel("status/triage");
        GHLabel areaTokenExchangeRepoLabel = mockLabel("area/token-exchange");

        when(labelIterator.hasNext()).thenReturn(true, true, true, true, false);
        when(labelIterator.next()).thenReturn(kindBugRepoLabel, kindEnhancementRepoLabel, statusTriageRepoLabel, areaTokenExchangeRepoLabel);
        when(repository.listLabels()).thenReturn(labelIterable);
    }

    @Test
    public void testBugWithKindLabel() throws IOException {
        String body = getResource("utils/issue-body-token-exchange");
        GHLabel kindBugLabel = mockLabel("kind/bug");

        when(issue.getBody()).thenReturn(body);
        when(issue.getLabels()).thenReturn(List.of(kindBugLabel));

        new AddAreaLabelToBugs().onOpen(issuePayload);

        verify(issue, never()).addLabels("kind/bug");
        verify(issue, never()).addLabels("status/triage");
        verify(issue).addLabels("area/token-exchange");
    }

    @Test
    public void testBugTemplateWithoutKindLabel() throws IOException {
        String body = getResource("utils/issue-body-token-exchange");

        when(issue.getBody()).thenReturn(body);
        when(issue.getLabels()).thenReturn(Collections.emptyList());

        new AddAreaLabelToBugs().onOpen(issuePayload);

        verify(issue).addLabels("kind/bug");
        verify(issue).addLabels("status/triage");
        verify(issue).addLabels("area/token-exchange");
    }

    @Test
    public void testEnhancementTemplateWithoutKindLabel() throws IOException {
        String body = getResource("utils/issue-body-enhancement");

        when(issue.getBody()).thenReturn(body);
        when(issue.getLabels()).thenReturn(Collections.emptyList());

        new AddAreaLabelToBugs().onOpen(issuePayload);

        verify(issue, never()).addLabels("kind/bug");
        verify(issue).addLabels("kind/enhancement");
        verify(issue).addLabels("status/triage");
    }

    @Test
    public void testPlainBodyWithoutKindLabel() throws IOException {
        String body = getResource("utils/issue-body-plain");

        when(issue.getBody()).thenReturn(body);
        when(issue.getLabels()).thenReturn(Collections.emptyList());

        new AddAreaLabelToBugs().onOpen(issuePayload);

        verify(issue, never()).addLabels("kind/bug");
        verify(issue, never()).addLabels("status/triage");
    }

    @Test
    public void testBugTemplateWithExistingKindEnhancement() throws IOException {
        String body = getResource("utils/issue-body-token-exchange");
        GHLabel kindEnhancementLabel = mockLabel("kind/enhancement");

        when(issue.getBody()).thenReturn(body);
        when(issue.getLabels()).thenReturn(List.of(kindEnhancementLabel));

        new AddAreaLabelToBugs().onOpen(issuePayload);

        verify(issue, never()).addLabels("kind/bug");
        verify(issue, never()).addLabels("status/triage");
        verify(issue, never()).addLabels("area/token-exchange");
    }

    private GHLabel mockLabel(String name) {
        GHLabel label = mock(GHLabel.class);
        when(label.getName()).thenReturn(name);
        return label;
    }

    private String getResource(String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            if (is == null) {
                throw new RuntimeException("Resource not found " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
