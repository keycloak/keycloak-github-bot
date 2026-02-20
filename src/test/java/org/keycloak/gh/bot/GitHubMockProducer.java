package org.keycloak.gh.bot;

import io.quarkus.test.Mock;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

/**
 * Produces mocked instances of GitHub components.
 * This satisfies dependencies for @Startup beans (like BugActionScheduleAutoBump)
 * while the real implementation is excluded via application.properties.
 */
@Singleton
public class GitHubMockProducer {

    @Produces
    @Mock
    public GitHubInstallationProvider createMockGitHubInstallationProvider() {
        GitHubInstallationProvider mock = Mockito.mock(GitHubInstallationProvider.class);

        // Stub methods to prevent NPEs in startup
        Mockito.when(mock.getRepositoryFullName()).thenReturn("keycloak/keycloak-test-bot");
        Mockito.when(mock.getBotLogin()).thenReturn("keycloak-test-bot");

        // Return a dummy GitHub client
        Mockito.when(mock.getGitHub()).thenReturn(Mockito.mock(GitHub.class));

        return mock;
    }
}