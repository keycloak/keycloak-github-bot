package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.WorkflowRun;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.FlakyTestParser;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewComment;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

public class ReportFlakyTests {

    Logger logger = Logger.getLogger(ReportFlakyTests.class);

    void onCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunEvent, GitHub gitHub) throws IOException {
        GHWorkflow workflow = workflowRunEvent.getWorkflow();
        GHWorkflowRun workflowRun = workflowRunEvent.getWorkflowRun();

        if(!workflow.getName().equals("Keycloak CI")) {
            logger.infov("Ignoring workflow {0}", workflowRun.getName());
            return;
        }

        List<FlakyTestParser.FlakyTest> flakyTests = findFlakyTests(workflowRun);
        if (flakyTests.isEmpty()) {
            logger.infov("No flakes found in {0}", workflowRun.getHtmlUrl());
            return;
        }

        GHPullRequest pullRequest = findPullRequest(workflowRun);
        if (GHEvent.PULL_REQUEST == workflowRun.getEvent() && pullRequest != null) {
            pullRequest.addLabels(Labels.FLAKY_TEST);
        }

        for (FlakyTestParser.FlakyTest flakyTest : flakyTests) {
            GHIssue issue = findIssue(gitHub, flakyTest);

            if (issue != null) {
                createIssueComment(flakyTest, workflowRun, pullRequest, issue);
            } else if (GHEvent.PULL_REQUEST == workflowRun.getEvent()) {
                if (pullRequest != null) {
                    createPullRequestReview(flakyTest, workflowRun, pullRequest);
                }
            } else {
                createIssue(flakyTest, workflowRun, null);
            }
        }
    }

    public List<FlakyTestParser.FlakyTest> findFlakyTests(GHWorkflowRun workflowRun) throws IOException {
        List<FlakyTestParser.FlakyTest> allFlakyTests = new LinkedList<>();

        PagedIterator<GHArtifact> iterator = workflowRun.listArtifacts().iterator();
        while (iterator.hasNext()) {
            GHArtifact ghArtifact = iterator.next();
            if (ghArtifact.getName().startsWith("flaky-tests-")) {
                List<FlakyTestParser.FlakyTest> flakyTests = ghArtifact.download(inputStream -> FlakyTestParser.parse(inputStream));
                allFlakyTests.addAll(flakyTests);
            }
        }

        return allFlakyTests;
    }

    public GHIssue findIssue(GitHub gitHub, FlakyTestParser.FlakyTest flakyTest) throws IOException {
        String title = getIssueTitle(flakyTest);
        List<GHIssue> issues = gitHub.searchIssues().isOpen().q("label:" + Labels.FLAKY_TEST + " in:title " + title).list().withPageSize(1).toList();
        return !issues.isEmpty() ? issues.get(0) : null;
    }

    public GHPullRequest findPullRequest(GHWorkflowRun workflowRun) throws IOException {
        if (GHEvent.PULL_REQUEST != workflowRun.getEvent()) {
            return null;
        }

        // workflowRun.getPullRequests() is empty for pull requests from a fork, although expensive listing through all
        // open pull requests seems to be the only way to find the pull request for the event.

        String headSha = workflowRun.getHeadSha();
        PagedIterable<GHPullRequest> pullRequestsForThisBranch = workflowRun.getRepository()
                .queryPullRequests()
                .state(GHIssueState.OPEN)
                .sort(GHPullRequestQueryBuilder.Sort.UPDATED)
                .direction(GHDirection.DESC)
                .list();

        for (GHPullRequest pullRequest : pullRequestsForThisBranch) {
            if (headSha.equals(pullRequest.getHead().getSha())) {
                return pullRequest;
            }
        }

        logger.errorv("Pull request event, but pull request not found for {0}", workflowRun.getHtmlUrl());
        return null;
    }

    public void createIssue(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun, GHPullRequest pullRequest) throws IOException {
        String title = getIssueTitle(flakyTest);
        String body = getIssueBody(flakyTest, workflowRun, pullRequest);
        GHIssue issue = workflowRun.getRepository()
                .createIssue(title)
                .body(body)
                .label(Labels.KIND_BUG)
                .label(Labels.AREA_CI)
                .label(Labels.FLAKY_TEST)
                .create();
        logger.infov("Flakes found in {0}, created issue {1}", workflowRun.getHtmlUrl(), issue.getHtmlUrl());
    }

    public void createIssueComment(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun, GHPullRequest pullRequest, GHIssue issue) throws IOException {
        String body = getIssueBody(flakyTest, workflowRun, pullRequest);
        issue.comment(body);

        logger.infov("Flakes found in {0}, added comment to existing issue {1}", workflowRun.getHtmlUrl(), issue.getHtmlUrl());
    }

    public void createPullRequestReview(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun, GHPullRequest pullRequest) throws IOException {
        String body = getPullRequestReviewBody(flakyTest, workflowRun, pullRequest);
        pullRequest
                .createReview()
                .event(GHPullRequestReviewEvent.REQUEST_CHANGES)
                .body(body)
                .create();
    }

    public String getIssueTitle(FlakyTestParser.FlakyTest flakyTest) {
        return "Flaky test: " + flakyTest.getClassName() + "#" + flakyTest.getMethodName();
    }

    public String getIssueBody(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun, GHPullRequest pullRequest) throws IOException {
        StringBuilder body = new StringBuilder();

        body.append("## ");
        body.append(flakyTest.getClassName());
        body.append("#");
        body.append(flakyTest.getMethodName());
        body.append("\n");

        body.append("[Run (");
        body.append(workflowRun.getEvent().name().toLowerCase());
        body.append(")](");
        body.append(workflowRun.getHtmlUrl().toString());
        body.append(")");

        if (GHEvent.PULL_REQUEST == workflowRun.getEvent() && pullRequest != null) {
            String pullRequestUrl = pullRequest.getHtmlUrl().toString();
            String pullRequestNumber = pullRequestUrl.substring(pullRequestUrl.lastIndexOf('/') + 1);

            body.append(" / [Pull Request #");
            body.append(pullRequestNumber);
            body.append("](");
            body.append(pullRequestUrl);
            body.append(")");
        }

        body.append("\n\n");

        body.append("### Errors\n");

        for (String failure : flakyTest.getFailures()) {
            body.append("\n```\n");
            body.append(failure);
            body.append("\n```\n");
        }

        return body.toString();
    }

    public String getPullRequestReviewBody(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun, GHPullRequest pullRequest) throws IOException {
        StringBuilder body = new StringBuilder();

        body.append("## Unreported flaky test detected\n");
        body.append("### ");
        body.append(flakyTest.getClassName());
        body.append("#");
        body.append(flakyTest.getMethodName());
        body.append("\n\n");

        String issueTitle = URLEncoder.encode(getIssueTitle(flakyTest), StandardCharsets.UTF_8);
        String issueBody = URLEncoder.encode(getIssueBody(flakyTest, workflowRun, pullRequest), StandardCharsets.UTF_8);
        String issueLabels = URLEncoder.encode(Labels.FLAKY_TEST + "," + Labels.AREA_CI + "," + Labels.KIND_BUG, StandardCharsets.UTF_8);

        body.append("If the test is affected by these changes, please review and update the changes accordingly.\n\n");
        body.append("Otherwise, a maintainer should [report the flaky test here](");
        body.append(workflowRun.getRepository().getHtmlUrl());
        body.append("/issues/new");
        body.append("?title=");
        body.append(issueTitle);
        body.append("&labels=");
        body.append(issueLabels);
        body.append("&body=");
        body.append(issueBody);
        body.append("), and re-run the failed jobs afterwards.\n\n");

        body.append("### Errors\n");

        for (String failure : flakyTest.getFailures()) {
            body.append("\n```\n");
            body.append(failure);
            body.append("\n```\n");
        }

        return body.toString();
    }

}
