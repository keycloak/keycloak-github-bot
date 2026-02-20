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
    public void init() throws java.io.IOException {
        GitHub appClient = gitHubClientProvider.getApplicationClient();
        GHApp app = appClient.getApp();
        this.botLogin = app.getSlug() + "[bot]";
        LOG.infof("GitHub App Provider initialized globally. Bot Identity: %s", botLogin);
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
}