package org.keycloak.gh.bot.cli;

import org.kohsuke.github.GHCheckRun;
import org.kohsuke.github.GHCommentAuthorAssociation;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.ReactionContent;

import java.io.IOException;
import java.util.List;
import java.util.Set;

//@Cli(name = "@bot", commands = { KeycloakBotCli.RerunCommand.class })
public class KeycloakBotCli {

    interface Commands {
        void run(GHEventPayload.IssueComment payload, GitHub gitHub) throws IOException;
    }

//    @Command(name = "rerun")
    static class RerunCommand implements Commands {

        private Set<GHCommentAuthorAssociation> ALLOWED = Set.of(GHCommentAuthorAssociation.OWNER, GHCommentAuthorAssociation.MEMBER);

        public void run(GHEventPayload.IssueComment payload, GitHub gitHub) throws IOException {
            if (!ALLOWED.contains(payload.getComment().getAuthorAssociation())) {
                payload.getComment().createReaction(ReactionContent.MINUS_ONE);
                return;
            }

            GHRepository repository = payload.getRepository();
            GHIssue issue = payload.getIssue();
            GHIssueComment comment = payload.getComment();

            if (!issue.isPullRequest()) {
                comment.createReaction(ReactionContent.MINUS_ONE);
                return;
            }

            GHPullRequest pullRequest = repository.getPullRequest(issue.getNumber());

            List<GHWorkflowRun> ghWorkflowRuns = repository.queryWorkflowRuns()
                    .branch(pullRequest.getHead().getRef())
                    .event(GHEvent.PULL_REQUEST)
                    .actor(pullRequest.getUser())
                    .status(GHWorkflowRun.Status.COMPLETED)
                    .list().toList();

            for (GHWorkflowRun r : ghWorkflowRuns) {
                if (r.getConclusion().equals(GHWorkflowRun.Conclusion.FAILURE) ||
                        r.getConclusion().equals(GHWorkflowRun.Conclusion.FAILURE) ||
                        r.getConclusion().equals(GHWorkflowRun.Conclusion.FAILURE)) {
                    System.out.println("Rerunning: " + r.getName());
                    r.rerun();
                }
            }


            for (GHCheckRun checkRun : repository.getCommit(pullRequest.getHead().getSha()).getCheckRuns()) {
                GHCheckRun.Conclusion conclusion = checkRun.getConclusion();

                if (!(conclusion.equals(GHCheckRun.Conclusion.FAILURE) ||
                        conclusion.equals(GHCheckRun.Conclusion.CANCELLED) ||
                        conclusion.equals(GHCheckRun.Conclusion.TIMED_OUT))) {
                    continue;
                }

                if (!checkRun.getApp().getSlug().equals("github-actions")) {
                    continue;
                }

            }


            //

            for (GHCheckRun r : repository.getCheckRuns(pullRequest.getMergeCommitSha())) {
                System.out.println("Conclusion : " + r.getConclusion());

            }

            System.out.println("Hello s");

            comment.createReaction(ReactionContent.PLUS_ONE);
        }

    }

}
