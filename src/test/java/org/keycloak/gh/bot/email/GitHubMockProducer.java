package org.keycloak.gh.bot.email;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

@ApplicationScoped
public class GitHubMockProducer {

    @Produces
    @Mock
    @ApplicationScoped
    public GitHubInstallationProvider createMock() {
        GitHubInstallationProvider mock = Mockito.mock(GitHubInstallationProvider.class);

        Mockito.when(mock.getGitHub()).thenReturn(Mockito.mock(GitHub.class));
        Mockito.when(mock.getRepositoryFullName()).thenReturn("keycloak/keycloak");

        return mock;
    }
}