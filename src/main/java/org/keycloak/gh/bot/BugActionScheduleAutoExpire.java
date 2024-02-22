package org.keycloak.gh.bot;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.labels.Priority;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.utils.DateUtil;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Startup
@Singleton
public class BugActionScheduleAutoExpire {

    private static final Logger logger = Logger.getLogger(BugActionScheduleAutoExpire.class);

    @ConfigProperty(name = "autoExpire.low.expiresDays")
    private long lowPriorityExpiresDays;
    @ConfigProperty(name = "autoExpire.normal.expiresDays")
    private long normalPriorityExpiresDays;

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @Inject
    BugActionMessages messages;

    String rootQuery;

    @PostConstruct
    public void init() {
        rootQuery = "repo:" + gitHubProvider.getRepositoryFullName() + " is:issue is:open label:" + Kind.BUG.toLabel() + " label:" + Status.AUTO_EXPIRE.toLabel();
    }

    @Scheduled(cron = "{autoExpire.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkIssuesWithLowAndNormalPriority() throws IOException {
        logger.info("Checking issues with low and normal priority");
        GitHub gitHub = gitHubProvider.getGitHub();

        expire(gitHub, Priority.LOW, normalPriorityExpiresDays);
        expire(gitHub, Priority.NORMAL, normalPriorityExpiresDays);
    }

    private void expire(GitHub gitHub, Priority priority, long days) throws IOException {
        String query = rootQuery + " label:" + priority + " updated:<" + DateUtil.minusDaysString(lowPriorityExpiresDays);
        String message = messages.getExpireComment(days, TimeUnit.DAYS);

        logger.debugv("Query: {0}", query);

        PagedIterator<GHIssue> itr = gitHub.searchIssues().q(query).list().iterator();
        while (itr.hasNext()) {
            GHIssue issue = itr.next();
            issue.comment(message);
            issue.addLabels(Status.EXPIRED_BY_BOT.toLabel());
            issue.close(GHIssueStateReason.NOT_PLANNED);
            logger.infov("Expired issue={0}", issue.getNumber());
        }
    }

}
