package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.GitHubEvent;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SyncKindLabelsTest {

    private JsonObject payload;
    private GitHub gitHub;
    private GHRepository repository;
    private GitHubEvent event;
    private GHIssue issue;

    private void runEvent(boolean typed, String payloadResource) throws IOException {
        payload = new JsonObject(getResource(payloadResource));

        gitHub = mock(GitHub.class);
        event = mock(GitHubEvent.class);
        repository = mock(GHRepository.class);
        issue = mock(GHIssue.class);

        when(event.getParsedPayload()).thenReturn(payload);
        when(event.getRepositoryOrThrow()).thenReturn("myorg/myrep");
        when(gitHub.getRepository("myorg/myrep")).thenReturn(repository);
        when(repository.getIssue(1234)).thenReturn(issue);

        SyncKindLabels syncKindLabels = new SyncKindLabels();
        if (typed) {
            syncKindLabels.onTyped(event, gitHub);
        } else {
            syncKindLabels.onUntyped(event, gitHub);
        }
    }

    @AfterEach
    public void after() {
        verifyNoMoreInteractions(issue);
    }

    @Test
    public void testTypedLabelMissing() throws IOException {
        runEvent(true, "typed-label-missing.json");
        verify(issue).addLabels("kind/cve");
    }

    @Test
    public void testTypedLabelMissingHasAnother() throws IOException {
        runEvent(true, "typed-label-missing-has-another.json");
        verify(issue).addLabels("kind/cve");
        verify(issue).removeLabels("kind/bug");
    }

    @Test
    public void testTypedLabelMatches() throws IOException {
        runEvent(true, "typed-label-matches.json");
    }

    @Test
    public void testTypedLabelMatchesHasAnother() throws IOException {
        runEvent(true, "typed-label-matches-has-another.json");
        verify(issue).removeLabels("kind/bug");
    }

    @Test
    public void testUntypedWithLabels() throws IOException {
        runEvent(false,"untyped-with-labels.json");
        verify(issue).removeLabels("kind/bug", "kind/cve");
    }

    @Test
    public void testUntypedWithoutLabels() throws IOException {
        runEvent(false,"untyped-without-labels.json");
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
