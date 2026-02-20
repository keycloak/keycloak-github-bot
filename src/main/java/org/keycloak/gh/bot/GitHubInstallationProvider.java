package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides application-wide GitHub configuration and dynamically resolves installation contexts.
 */
@Startup
@Singleton
public class GitHubInstallationProvider {

    private static final Logger LOG = Logger.getLogger(GitHubInstallationProvider.class);

    @Inject
    GitHubClientProvider gitHubClientProvider;

    private String botLogin;

    @PostConstruct
    public void init() {
        try {
            GitHub appClient = gitHubClientProvider.getApplicationClient();
            GHApp app = appClient.getApp();
            this.botLogin = app.getSlug() + "[bot]";
            LOG.infof("GitHub App Provider initialized globally. Bot Identity: %s", botLogin);
        } catch (IOException e) {
            LOG.error("Critical failure: Could not initialize GitHub App identity.", e);
            throw new RuntimeException(e);
        }
    }

    public String getBotLogin() {
        return botLogin;
    }

    /**
     * Resolves all active repositories across all installations.
     */
    public Map<GHRepository, GitHub> getAllInstalledRepositories() throws IOException {
        Map<GHRepository, GitHub> repositories = new HashMap<>();
        GitHub appClient = gitHubClientProvider.getApplicationClient();

        for (GHAppInstallation installation : appClient.getApp().listInstallations()) {
            GitHub installationClient = gitHubClientProvider.getInstallationClient(installation.getId());
            for (GHRepository repo : installationClient.getInstallation().listRepositories()) {
                repositories.put(repo, installationClient);
            }
        }
        return repositories;
    }

    /**
     * Dynamically resolves an authenticated client for a specific repository.
     */
    public GitHub getInstallationClient(String repositoryFullName) throws IOException {
        GitHub appClient = gitHubClientProvider.getApplicationClient();
        String[] parts = repositoryFullName.split("/");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid repository format. Expected 'owner/repo'");
        }

        GHAppInstallation installation = appClient.getApp().getInstallationByRepository(parts[0], parts[1]);
        if (installation == null) {
            throw new IllegalStateException("App is not installed on repository: " + repositoryFullName);
        }

        return gitHubClientProvider.getInstallationClient(installation.getId());
    }
}