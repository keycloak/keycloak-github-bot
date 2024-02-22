package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.GitHubClientProvider;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GitHub;

import java.io.IOException;

@Startup
@Singleton
public class GitHubInstallationProvider {

    private static final Logger logger = Logger.getLogger(GitHubInstallationProvider.class);

    @Inject
    GitHubClientProvider gitHubClientProvider;

    private long installationId = -1;
    private String botLogin = null;
    private String repositoryFullName = null;

    @PostConstruct
    public void init() throws IOException {
        GitHub appClient = gitHubClientProvider.getApplicationClient();
        GHApp app = appClient.getApp();

        GHAppInstallation installation = app.listInstallations().iterator().next();
        botLogin = app.getSlug() + "[bot]";

        installationId = installation.getId();

        GitHub installationClient = gitHubClientProvider.getInstallationClient(installationId);
        repositoryFullName = installationClient.getInstallation().listRepositories().iterator().next().getFullName();

        logger.infov("Init: repository={0}, bot={1}, installation={2}", repositoryFullName, botLogin, installationId);
    }

    public GitHub getGitHub() {
        return gitHubClientProvider.getInstallationClient(installationId);
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public String getBotLogin() {
        return botLogin;
    }

}
