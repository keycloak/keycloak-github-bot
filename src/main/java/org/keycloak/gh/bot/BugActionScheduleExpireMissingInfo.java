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
import java.util.stream.Collectors;

@Startup
@Singleton
public class BugActionScheduleExpireMissingInfo {

    private static final Logger logger = Logger.getLogger(BugActionScheduleExpireMissingInfo.class);

    @ConfigProperty(name = "missingInfo.expiration.unit")
    private TimeUnit expirationUnit;
    @ConfigProperty(name = "missingInfo.expiration.value")
    private long expirationValue;

    private final LastChecked lastChecked = new LastChecked();

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @Inject
    BugActionMessages messages;

    @Scheduled(cron = "{missingInfo.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkIssuesWithMissingInformation() throws IOException {
        logger.info("Checking issues with missing information");
        GitHub gitHub = gitHubProvider.getGitHub();

        GHRepository repository = gitHub.getRepository(gitHubProvider.getRepositoryFullName());

        PagedIterator<GHIssue> missingInfoItr = repository.queryIssues().label(Status.MISSING_INFORMATION.toLabel()).label(Status.AUTO_EXPIRE.toLabel()).list().iterator();

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
                    logger.warnv("Bot comment not found: issue={0}", issue.getNumber());
                } else {
                    lastChecked.checked(issue, lastBotComment);
                }
            } else {
                long lastBotComment = lastChecked.getLastBotComment(issue).getTime();
                long expires = lastBotComment + expirationUnit.toMillis(expirationValue);

                if (System.currentTimeMillis() > expires) {
                    String comment = messages.getExpireComment(expirationValue, expirationUnit);
                    issue.comment(comment);
                    issue.removeLabels(Status.MISSING_INFORMATION.toLabel());
                    issue.close(GHIssueStateReason.NOT_PLANNED);
                    lastChecked.remove(issue);
                    logger.infov("Expired: issue={0}", issue.getNumber());
                }
            }
        }

        lastChecked.clean();
    }

    public class LastChecked {

        Map<Integer, Date> lastBotComment = new HashMap<>();

        Set<Integer> visited = new HashSet<>();

        public boolean shouldCheck(GHIssue issue) {
            int issueNumber = issue.getNumber();
            visited.add(issueNumber);
            return !lastBotComment.containsKey(issueNumber);
        }

        public void checked(GHIssue issue, GHIssueComment issueLastBotComment) throws IOException {
            int issueNumber = issue.getNumber();
            visited.add(issueNumber);
            lastBotComment.put(issueNumber, issueLastBotComment.getUpdatedAt());
        }

        public void remove(GHIssue issue) {
            visited.remove(issue.getNumber());
            lastBotComment.remove(issue.getNumber());
        }

        public Date getLastBotComment(GHIssue issue) {
            return lastBotComment.get(issue.getNumber());
        }

        public void clean() {
            lastBotComment.keySet().removeIf(integer -> !visited.contains(integer));
            visited.clear();

            if (!lastBotComment.isEmpty()) {
                logger.infov("Monitoring: issues={0}", lastBotComment.keySet().stream().map(i -> Integer.toString(i)).collect(Collectors.joining(",")));
            }
        }

    }

}
