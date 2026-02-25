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
     * Fetches the authenticated GitHub client for a specific repository
     */
    public GitHub getGitHubClient(String repositoryFullName) throws IOException {
        int slashIndex = repositoryFullName.indexOf('/');

        if (slashIndex <= 0 || slashIndex == repositoryFullName.length() - 1) {
            throw new IllegalArgumentException("Invalid repository format. Expected 'owner/repo', got: " + repositoryFullName);
        }

        String owner = repositoryFullName.substring(0, slashIndex);
        String repoName = repositoryFullName.substring(slashIndex + 1);

        long installId = gitHubClientProvider.getApplicationClient().getApp()
                .getInstallationByRepository(owner, repoName).getId();

        return gitHubClientProvider.getInstallationClient(installId);
    }
}