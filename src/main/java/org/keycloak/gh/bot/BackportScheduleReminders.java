package org.keycloak.gh.bot;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.BackportLabels;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Startup
@Singleton
public class BackportScheduleReminders {

    private static final Logger logger = Logger.getLogger(BackportScheduleReminders.class);
    private static final Duration INITIAL_DELAY = Duration.ofHours(24);

    @ConfigProperty(name = "backport.reminderIntervalDays")
    long reminderIntervalDays;

    @ConfigProperty(name = "repository.mainRepository")
    String mainRepository;

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @Inject
    BackportPullRequests pullRequests;

    @Scheduled(cron = "{backport.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkBackports() throws IOException {
        logger.info("Checking issues for missing backports");
        GHRepository repository = gitHubProvider.getGitHubClient(mainRepository).getRepository(mainRepository);
        Set<String> repositoryBranches = repository.getBranches().keySet();
        Set<Integer> visited = new HashSet<>();
        Instant now = Instant.now();
        for (GHLabel label : repository.listLabels()) {
            if (!BackportLabels.isRequired(label.getName(), repositoryBranches)) {
                continue;
            }
            for (GHIssue issue : repository.queryIssues().state(GHIssueState.CLOSED).label(label.getName()).list()) {
                if (!issue.isPullRequest() && visited.add(issue.getNumber())) {
                    try {
                        if (isEligible(issue, now)) {
                            checkIssue(repository.getIssue(issue.getNumber()), now, repositoryBranches);
                        }
                    } catch (IOException e) {
                        logger.errorv(e, "Failed checking backports for issue={0}", issue.getNumber());
                    }
                }
            }
        }
    }

    void checkIssue(GHIssue issue, Instant now, Set<String> repositoryBranches) throws IOException {
        if (!isEligible(issue, now)) {
            return;
        }
        Set<String> branches = BackportLabels.requiredBranches(issue.getLabels(), repositoryBranches);
        if (branches.isEmpty()) {
            return;
        }

        BackportPullRequests.Result result = pullRequests.check(issue, branches);
        if (!result.mainMerged() || result.missingBranches().isEmpty()) {
            return;
        }

        String marker = "<!-- backport-reminder:closed-at=" + issue.getClosedAt().getTime() + " -->";
        if (shouldRemind(issue, marker, now)) {
            issue.comment(reminderMessage(issue, result.missingBranches(), marker));
            logger.infov("Reminded issue={0}, missing backports={1}", issue.getNumber(), result.missingBranches());
        }
    }

    private boolean isEligible(GHIssue issue, Instant now) {
        return issue.getState() == GHIssueState.CLOSED && issue.getStateReason() == GHIssueStateReason.COMPLETED
                && issue.getClosedAt() != null && !issue.isLocked()
                && !now.isBefore(issue.getClosedAt().toInstant().plus(INITIAL_DELAY));
    }

    private boolean shouldRemind(GHIssue issue, String marker, Instant now) throws IOException {
        Instant lastReminder = null;
        String botLogin = gitHubProvider.getBotLogin();
        for (GHIssueComment comment : issue.listComments()) {
            if (comment.getBody().contains(marker) && comment.getUser().getLogin().equals(botLogin)) {
                Instant createdAt = comment.getCreatedAt().toInstant();
                if (lastReminder == null || createdAt.isAfter(lastReminder)) {
                    lastReminder = createdAt;
                }
            }
        }
        return lastReminder == null || !now.isBefore(lastReminder.plus(Duration.ofDays(reminderIntervalDays)));
    }

    private String reminderMessage(GHIssue issue, Set<String> missingBranches, String marker) {
        String assignees = issue.getAssignees().stream().map(user -> "@" + user.getLogin()).collect(Collectors.joining(" "));
        String missing = missingBranches.stream().map(branch -> "`" + branch + "`").collect(Collectors.joining(", "));
        return (assignees.isEmpty() ? "" : assignees + " ")
                + "This issue is fixed in `main`, but backport PRs are still missing for: " + missing + ".\n\n"
                + "Please create or link the backport PRs in Development. Include `Closes #" + issue.getNumber()
                + "` in their descriptions for automatic linking when opened or updated. "
                + "If a backport is no longer required, remove its `backport/*` label.\n\n" + marker;
    }
}
