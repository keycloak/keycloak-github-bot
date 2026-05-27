package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.PullRequest;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.CommitUtils;
import org.kohsuke.github.*;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SyncPRTeamLabels {

    private static final Logger logger = Logger.getLogger(SyncPRTeamLabels.class);
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(SyncPRTeamLabels.class);

    void onOpen(@PullRequest.Opened GHEventPayload.PullRequest payload) throws IOException {
        GHPullRequest pullRequest = payload.getPullRequest();
        List<String> commitMessages = pullRequest.listCommits().toList().stream().map(i -> i.getCommit().getMessage()).toList();

        Set<String> teamLabelsToAdd = new HashSet<>();

        for (String commitMessage : commitMessages) {
            Integer issuerNumber = CommitUtils.getIssuerNumber(commitMessage);
            if (issuerNumber != null) {
                GHRepository repository = payload.getRepository();
                GHIssue issue = repository.getIssue(issuerNumber);
                logger.infov("PR {0} {1} linked to issue {2} {3}", pullRequest.getNumber(), pullRequest.getTitle(), issue.getNumber(), issue.getTitle());

                issue.getLabels().stream().map(GHLabel::getName).filter(l -> l.startsWith("team/")).forEach(teamLabelsToAdd::add);
            }
        }

        if (!teamLabelsToAdd.isEmpty()) {
            logger.infov("Adding labels {0} to PR {1}", String.join(",", teamLabelsToAdd), pullRequest.getNumber());
            pullRequest.addLabels(teamLabelsToAdd.toArray(new String[0]));
        } else {
            logger.infov("No team labels found for PR {0}", pullRequest.getNumber());
        }
    }



}
