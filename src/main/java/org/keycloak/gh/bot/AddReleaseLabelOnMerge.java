package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.Labels;
import org.keycloak.gh.bot.utils.ReleaseLabels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Replaces the labeller GitHub workflow of keycloak/keycloak, which relied on 'pull_request_target'.
 * When a PR is merged, the issues it closes get the 'release/...' label and lose the 'backport/...' label for the target branch.
 */
public class AddReleaseLabelOnMerge {

    private static final Logger logger = Logger.getLogger(AddReleaseLabelOnMerge.class);

    void onClose(@PullRequest.Closed GHEventPayload.PullRequest payload) throws IOException {
        GHPullRequest pullRequest = payload.getPullRequest();
        if (!pullRequest.isMerged()) {
            return;
        }

        GHRepository repository = payload.getRepository();
        String base = pullRequest.getBase().getRef();

        // Only fetch what is needed for the target branch
        List<String> branches = base.equals("main") ? repository.getBranches().keySet().stream().toList() : List.of();
        List<String> tags = base.startsWith("release/") ? repository.listTags().toList().stream().map(GHTag::getName).toList() : List.of();

        ReleaseLabels.Result labels = ReleaseLabels.forBaseBranch(base, branches, tags);
        if (labels == null) {
            logger.infof("PR %s merged into %s: no release label applicable", pullRequest.getNumber(), base);
            return;
        }

        Set<Integer> issueNumbers = ReleaseLabels.findIssues(repository.getFullName(), pullRequest.getBody(), null);
        String mergeCommitSha = pullRequest.getMergeCommitSha();
        if (issueNumbers.isEmpty() && mergeCommitSha != null) {
            String commitMessage = repository.getCommit(mergeCommitSha).getCommitShortInfo().getMessage();
            issueNumbers = ReleaseLabels.findIssues(repository.getFullName(), pullRequest.getBody(), commitMessage);
        }
        if (issueNumbers.isEmpty()) {
            logger.infof("PR %s merged into %s: no linked issues found", pullRequest.getNumber(), base);
            return;
        }

        createLabelIfMissing(repository, labels.releaseLabel());

        for (int issueNumber : issueNumbers) {
            GHIssue issue;
            try {
                issue = repository.getIssue(issueNumber);
            } catch (GHFileNotFoundException e) {
                logger.errorf(e, "PR %s merged into %s: referenced issue %s not found", pullRequest.getNumber(), base, issueNumber);
                continue;
            }
            if (issue.isPullRequest()) {
                continue;
            }
            issue.addLabels(labels.releaseLabel());
            if (issue.getLabels().stream().anyMatch(l -> l.getName().equals(labels.backportLabel()))) {
                issue.removeLabel(labels.backportLabel());
            }
            logger.infof("PR %s merged into %s: labelled issue %s with %s", pullRequest.getNumber(), base, issueNumber, labels.releaseLabel());
        }
    }

    private void createLabelIfMissing(GHRepository repository, String label) throws IOException {
        if (Labels.hasLabel(repository, label)) {
            return;
        }
        try {
            repository.createLabel(label, "0E8A16");
        } catch (IOException e) {
            // A concurrent merge for the same release might have created the label in the meantime
            if (!Labels.hasLabel(repository, label)) {
                throw e;
            }
        }
    }

}
