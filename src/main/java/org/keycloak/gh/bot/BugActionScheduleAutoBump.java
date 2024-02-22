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
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Startup
@Singleton
public class BugActionScheduleAutoBump {

    private static final Logger logger = Logger.getLogger(BugActionScheduleAutoBump.class);

    @ConfigProperty(name = "autoBump.low.reactions")
    int bumpLowReactions;
    @ConfigProperty(name = "autoBump.normal.reactions")
    int bumpNormalReactions;

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @Inject
    BugActionMessages messages;

    String rootQuery;

    @PostConstruct
    public void init() {
        rootQuery = "repo:" + gitHubProvider.getRepositoryFullName() + " is:issue is:open label:" + Kind.BUG + " label:" + Status.AUTO_EXPIRE;
    }

    @Scheduled(cron = "{autoBump.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void checkIssuesWithLowAndNormalPriority() throws IOException {
        logger.info("Checking issues with auto-bump");
        GitHub gitHub = gitHubProvider.getGitHub();

        bump(gitHub, Priority.LOW, bumpLowReactions, Priority.NORMAL);
        bump(gitHub, Priority.NORMAL, bumpNormalReactions, Priority.IMPORTANT);
    }

    private void bump(GitHub gitHub, Priority currentPriority, int bumpReactions, Priority newPriority) throws IOException {
        String query = rootQuery + " label:" + currentPriority + " reactions:>=" + bumpReactions;

        logger.debugv("Query: {0}", query);

        PagedIterator<GHIssue> itr = gitHub.searchIssues().q(query).list().iterator();
        while (itr.hasNext()) {
            GHIssue issue = itr.next();

            List<String> removeLabels = new LinkedList<>();
            removeLabels.add(currentPriority.toLabel());
            if (newPriority.equals(Priority.IMPORTANT)) {
                removeLabels.add(Status.AUTO_BUMP.toLabel());
                removeLabels.add(Status.AUTO_EXPIRE.toLabel());
            }

            issue.removeLabels(removeLabels.toArray(new String[0]));
            issue.addLabels(newPriority.toLabel(), Status.BUMPED_BY_BOT.toLabel());
            logger.infov("Bumped issue={0}, from={1}, to={2}", issue.getNumber(), currentPriority, newPriority);
        }
    }

}
