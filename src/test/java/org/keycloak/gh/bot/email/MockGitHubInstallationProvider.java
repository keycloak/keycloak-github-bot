package org.keycloak.gh.bot.email;

import io.quarkus.test.Mock;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

@Mock
@Singleton
public class MockGitHubInstallationProvider extends GitHubInstallationProvider {
    @Override
    @PostConstruct
    public void init() {
    }

    @Override
    public GitHub getGitHub() {
        return Mockito.mock(GitHub.class);
    }
}