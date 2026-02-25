package org.keycloak.gh.bot;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Status;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Startup
@Singleton
public class BugActionScheduleExpireMissingInfo {

    private static final Logger logger = Logger.getLogger(BugActionScheduleExpireMissingInfo.class);

    @ConfigProperty(name = "missingInfo.expiration.unit")
    private TimeUnit expirationUnit;
    @ConfigProperty(name = "missingInfo.expiration.value")
    private long expirationValue;

    @ConfigProperty(name = "repository.mainRepository")
    String mainRepository;

    private final LastChecked lastChecked = new LastChecked();

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @Inject
    BugActionMessages messages;

    @Scheduled(cron = "{missingInfo.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkIssuesWithMissingInformation() throws IOException {
        logger.infov("Checking issues with missing information for repository: {0}", mainRepository);

        GitHub gitHub = gitHubProvider.getGitHubClient(mainRepository);
        GHRepository repository = gitHub.getRepository(mainRepository);

        PagedIterator<GHIssue> missingInfoItr = repository.queryIssues()
                .label(Status.MISSING_INFORMATION.toLabel())
                .label(Status.AUTO_EXPIRE.toLabel())
                .list().iterator();

        while (missingInfoItr.hasNext()) {
            GHIssue issue = missingInfoItr.next();

            if (lastChecked.shouldCheck(issue)) {
                GHIssueComment lastBotComment = null;

                for (GHIssueComment c : issue.getComments()) {
                    if (c.getUser().getLogin().equals(gitHubProvider.getBotLogin())) {
                        if (lastBotComment == null || lastBotComment.getUpdatedAt().before(c.getUpdatedAt())) {
                            lastBotComment = c;
                        }
                    }
                }

                if (lastBotComment == null) {
                    logger.warnv("Bot comment not found: issue={0} in repo={1}", issue.getNumber(), repository.getFullName());
                } else {
                    lastChecked.checked(issue, lastBotComment);
                }
            } else {
                long lastBotCommentTime = lastChecked.getLastBotComment(issue).getTime();
                long expires = lastBotCommentTime + expirationUnit.toMillis(expirationValue);

                if (System.currentTimeMillis() > expires) {
                    String comment = messages.getExpireComment(expirationValue, expirationUnit);
                    issue.comment(comment);
                    issue.removeLabels(Status.MISSING_INFORMATION.toLabel());
                    issue.addLabels(Status.EXPIRED_BY_BOT.toLabel());
                    issue.close(GHIssueStateReason.NOT_PLANNED);
                    lastChecked.remove(issue);
                    logger.infov("Expired: issue={0} in repo={1}", issue.getNumber(), repository.getFullName());
                }
            }
        }

        // Cleans up memory for issues that have been handled or no longer meet the criteria
        lastChecked.clean();
    }

    public class LastChecked {

        Map<Long, Date> lastBotComment = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        public boolean shouldCheck(GHIssue issue) {
            long issueId = issue.getId();
            visited.add(issueId);
            return !lastBotComment.containsKey(issueId);
        }

        public void checked(GHIssue issue, GHIssueComment issueLastBotComment) throws IOException {
            long issueId = issue.getId();
            visited.add(issueId);
            lastBotComment.put(issueId, issueLastBotComment.getUpdatedAt());
        }

        public void remove(GHIssue issue) {
            visited.remove(issue.getId());
            lastBotComment.remove(issue.getId());
        }

        public Date getLastBotComment(GHIssue issue) {
            return lastBotComment.get(issue.getId());
        }

        public void clean() {
            lastBotComment.keySet().removeIf(id -> !visited.contains(id));
            visited.clear();

            if (!lastBotComment.isEmpty()) {
                logger.infov("Monitoring: {0} issues for missing info", lastBotComment.size());
            }
        }
    }
}