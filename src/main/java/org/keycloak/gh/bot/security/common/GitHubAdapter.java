package org.keycloak.gh.bot.security.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.GitHubInstallationProvider;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHContentUpdateResponse;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedSearchIterable;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GitHubAdapter {

    private static final Logger LOG = Logger.getLogger(GitHubAdapter.class);

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @ConfigProperty(name = "keycloak.security.repository")
    String allowedRepository;

    public boolean isAccessDenied() {
        String currentRepo = gitHubProvider.getRepositoryFullName();
        boolean denied = currentRepo == null || !currentRepo.equalsIgnoreCase(allowedRepository);
        if (denied) {
            LOG.debugf("SECURITY: Access denied. Repository mismatch: %s != %s", currentRepo, allowedRepository);
        }
        return denied;
    }

    private GHRepository getRepository() throws IOException {
        if (isAccessDenied()) {
            throw new IllegalStateException("Bot is not connected to the allowed repository.");
        }
        return gitHubProvider.getGitHub().getRepository(gitHubProvider.getRepositoryFullName());
    }

    public GHIssue createSecurityIssue(String subject, String body, String sourceLabel) throws IOException {
        GHIssue issue = getRepository().createIssue(subject)
                .body(body)
                .label(sourceLabel)
                .create();
        LOG.debugf("🆕 Created Security Issue #%d from source %s", issue.getNumber(), sourceLabel);
        return issue;
    }

    public void updateTitleAndLabels(GHIssue issue, String newTitle, String additionalLabel) throws IOException {
        issue.setTitle(newTitle);
        if (additionalLabel != null) {
            issue.addLabels(additionalLabel);
        }
        LOG.debugf("📝 Updated Issue #%d: Title='%s', Added Label='%s'", issue.getNumber(), newTitle, additionalLabel);
    }

    public String uploadFile(String threadId, String fileName, byte[] content) throws IOException {
        String path = "attachments/" + threadId + "/" + fileName;
        String message = "Upload attachment " + fileName + " for thread " + threadId;

        GHContentUpdateResponse response = uploadToRepo(path, content, message);

        GHContent ghContent = response.getContent();
        LOG.debugf("📁 Uploaded file %s to %s", fileName, path);
        return ghContent.getHtmlUrl();
    }

    protected GHContentUpdateResponse uploadToRepo(String path, byte[] content, String message) throws IOException {
        return getRepository().createContent()
                .path(path)
                .content(content)
                .message(message)
                .commit();
    }

    public Optional<GHIssue> findOpenEmailIssueByThreadId(String threadId) throws IOException {
        if (isAccessDenied()) return Optional.empty();

        String repoName = gitHubProvider.getRepositoryFullName();
        String query = String.format("repo:%s \"%s\" label:%s is:open is:issue",
                repoName, threadId, Constants.SOURCE_EMAIL);

        PagedSearchIterable<GHIssue> issues = gitHubProvider.getGitHub().searchIssues().q(query).list();
        for (GHIssue issue : issues) {
            return Optional.of(issue);
        }
        return Optional.empty();
    }

    public void commentOnIssue(GHIssue issue, String commentBody) throws IOException {
        issue.comment(commentBody);
        LOG.debugf("💬 Commented on Issue #%d", issue.getNumber());
    }

    public List<GHIssue> getIssuesUpdatedSince(Date since) throws IOException {
        return getRepository().queryIssues()
                .state(GHIssueState.OPEN)
                .since(since)
                .list()
                .toList();
    }

    public List<GHIssue> getOpenCveIssues() throws IOException {
        if (isAccessDenied()) return List.of();

        String repoName = gitHubProvider.getRepositoryFullName();
        String query = String.format("repo:%s label:%s -label:%s is:open",
                repoName, Constants.KIND_CVE, Constants.STATUS_JIRA_SYNCED);

        return gitHubProvider.getGitHub().searchIssues()
                .q(query)
                .list()
                .toList();
    }
}