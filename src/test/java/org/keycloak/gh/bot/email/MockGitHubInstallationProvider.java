package org.keycloak.gh.bot.email;

import io.quarkus.test.Mock;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.kohsuke.github.GitHub;
import org.mockito.Mockito;

@Mock
@Alternative
@Singleton
public class MockGitHubInstallationProvider extends GitHubInstallationProvider {
    @Override
    public void init() {
    }

    @Override
    public GitHub getGitHub() {
        return Mockito.mock(GitHub.class);
    }

    @Override
    public String getRepositoryFullName() {
        return "keycloak/keycloak-private";
    }
}