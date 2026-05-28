package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.CommitUtils;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class SyncPRTeamLabels {

    private static final Logger logger = Logger.getLogger(SyncPRTeamLabels.class);
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SyncPRTeamLabels.class);

    void onOpen(@PullRequest.Opened GHEventPayload.PullRequest payload) throws IOException {
        GHPullRequest pullRequest = payload.getPullRequest();

        List<GHIssue> linkedIssues = CommitUtils.linkedIssues(payload.getRepository(), pullRequest);
        List<String> teamLabels = linkedIssues.stream().flatMap(i -> i.getLabels().stream()).map(GHLabel::getName).filter(l -> l.startsWith("team/")).distinct().toList();

        if (!teamLabels.isEmpty()) {
            logger.infov("PR {0}: Adding labels {0} from issues {1}", pullRequest.getNumber(), teamLabels, linkedIssues.stream().map(GHIssue::getNumber).toList());
            pullRequest.addLabels(teamLabels.toArray(new String[0]));
        } else if (!linkedIssues.isEmpty()) {
            logger.infov("PR {0}: No team labels found in issues {1}", pullRequest.getNumber(), linkedIssues.stream().map(GHIssue::getNumber).toList());
        } else {
            logger.infov("PR {0}: No linked issues found", pullRequest.getNumber());
        }
    }

    void onLabelled(@PullRequest.Labeled GHEventPayload.PullRequest payload) throws IOException {
        GHPullRequest pullRequest = payload.getPullRequest();

        String[] teamLabels = pullRequest.getLabels().stream().map(GHLabel::getName).filter(l -> l.startsWith("team/")).distinct().toArray(String[]::new);

        List<GHIssue> linkedIssues = CommitUtils.linkedIssues(payload.getRepository(), pullRequest);
        for (GHIssue linkedIssue : linkedIssues) {
            logger.infov("Issue {0}: Adding labels {0} from issue {1}", linkedIssue.getNumber(), teamLabels, pullRequest.getNumber());
            linkedIssue.addLabels(teamLabels);
        }
    }

}
