package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.WorkflowRun;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.FlakyJob;
import org.keycloak.gh.bot.utils.FlakyJobParser;
import org.keycloak.gh.bot.utils.FlakyTest;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

public class ReportFlakyTests {

    Logger logger = Logger.getLogger(ReportFlakyTests.class);

    void onCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunEvent, GitHub gitHub) throws IOException {
        GHWorkflow workflow = workflowRunEvent.getWorkflow();
        GHWorkflowRun workflowRun = workflowRunEvent.getWorkflowRun();
        boolean isPullRequest = GHEvent.PULL_REQUEST == workflowRun.getEvent();

        if(!workflow.getName().equals("Keycloak CI")) {
            return;
        }

        if (GHEvent.PULL_REQUEST != workflowRun.getEvent() && !workflowRun.getHeadBranch().equals("main")) {
            logger.infov("Ignoring event for branch {0}", workflowRun.getHeadBranch());
            return;
        }

        List<FlakyJob> flakyJobs = findFlakyJobs(workflowRun);
        if (flakyJobs.isEmpty()) {
            logger.infov("No flakes found in {0}", workflowRun.getHtmlUrl());
            return;
        }

        GHPullRequest pullRequest = null;
        if (isPullRequest) {
            pullRequest = findPullRequest(workflow.getRepository(), flakyJobs);

            if (pullRequest != null) {
                pullRequest.addLabels(Labels.FLAKY_TEST);
            } else {
                logger.errorv("Pull request event, but pull request not found for {0}", workflowRun.getHtmlUrl());
            }
        }

        List<FlakyTest> unreportedFlakyTestsFromPr = new LinkedList<>();

        for (FlakyJob flakyJob : flakyJobs) {
            for (FlakyTest flakyTest : flakyJob.getFlakyTests()) {
                GHIssue issue = findIssue(gitHub, flakyTest);

                if (issue != null) {
                    createIssueComment(flakyTest, workflowRun, issue);
                } else if (isPullRequest) {
                    unreportedFlakyTestsFromPr.add(flakyTest);
                } else {
                    createIssue(flakyTest, workflowRun);
                }
            }
        }

        if (!unreportedFlakyTestsFromPr.isEmpty() && pullRequest != null) {
            createPullRequestReview(unreportedFlakyTestsFromPr, workflowRun, pullRequest);
        }
    }

    public List<FlakyJob> findFlakyJobs(GHWorkflowRun workflowRun) throws IOException {
        List<FlakyJob> allFlakyJobs = new LinkedList<>();

        PagedIterator<GHArtifact> iterator = workflowRun.listArtifacts().iterator();
        while (iterator.hasNext()) {
            GHArtifact ghArtifact = iterator.next();
            if (ghArtifact.getName().startsWith("flaky-tests-")) {
                FlakyJob flakyJob = ghArtifact.download(inputStream -> FlakyJobParser.parse(inputStream));
                flakyJob.setWorkflow(workflowRun.getName());
                allFlakyJobs.add(flakyJob);
            }
        }

        return allFlakyJobs;
    }

    public GHIssue findIssue(GitHub gitHub, FlakyTest flakyTest) throws IOException {
        String title = flakyTest.getIssueTitle();
        List<GHIssue> issues = gitHub.searchIssues().isOpen().q("label:" + Labels.FLAKY_TEST + " in:title " + title).list().withPageSize(1).toList();
        return !issues.isEmpty() ? issues.get(0) : null;
    }

    public GHPullRequest findPullRequest(GHRepository repository, List<FlakyJob> flakyJobs) throws IOException {
        for (FlakyJob flakyJob : flakyJobs) {
            if (flakyJob.getPr() != null) {
                return repository.getPullRequest(Integer.valueOf(flakyJob.getPr()));
            }
        }
        return null;
    }

    public void createIssue(FlakyTest flakyTest, GHWorkflowRun workflowRun) throws IOException {
        String title = flakyTest.getIssueTitle();
        String body = flakyTest.getIssueBody();
        GHIssue issue = workflowRun.getRepository()
                .createIssue(title)
                .body(body)
                .label(Labels.KIND_BUG)
                .label(Labels.AREA_CI)
                .label(Labels.FLAKY_TEST)
                .create();
        logger.infov("Flakes found in {0}, created issue {1}", workflowRun.getHtmlUrl(), issue.getHtmlUrl());
    }

    public void createIssueComment(FlakyTest flakyTest, GHWorkflowRun workflowRun, GHIssue issue) throws IOException {
        String body = flakyTest.getIssueBody();
        issue.comment(body);

        logger.infov("Flakes found in {0}, added comment to existing issue {1}", workflowRun.getHtmlUrl(), issue.getHtmlUrl());
    }

    public void createPullRequestReview(List<FlakyTest> flakyTests, GHWorkflowRun workflowRun, GHPullRequest pullRequest) throws IOException {
        String body = getPullRequestReviewBody(flakyTests, workflowRun.getRepository().getHtmlUrl());
        pullRequest
                .createReview()
                .event(GHPullRequestReviewEvent.REQUEST_CHANGES)
                .body(body)
                .create();
    }

    public String getPullRequestReviewBody(List<FlakyTest> flakyTests, URL repositoryUrl) {
        StringBuilder body = new StringBuilder();

        body.append("## Unreported flaky test detected\n");
        body.append("If the below flaky tests below are affected by the changes, please review and update the changes accordingly. Otherwise, a maintainer should report the flaky tests prior to merging the PR.\n\n");

        for (FlakyTest flakyTest : flakyTests) {
            body.append("### ");
            body.append(flakyTest.getClassName());
            body.append("#");
            body.append(flakyTest.getMethodName());
            body.append("\n\n");

            body.append("[");
            body.append(flakyTest.getFlakyJob().getWorkflow());
            body.append(" - ");
            body.append(flakyTest.getFlakyJob().getJobName());
            body.append("](");
            body.append(flakyTest.getFlakyJob().getJobUrl());
            body.append(")\n\n");

            for (String failure : flakyTest.getFailures()) {
                body.append("\n```\n");
                body.append(failure);
                body.append("\n```\n");
            }

            String issueTitle = URLEncoder.encode(flakyTest.getIssueTitle(), StandardCharsets.UTF_8);
            String issueBody = URLEncoder.encode(flakyTest.getIssueBody(), StandardCharsets.UTF_8);
            String issueLabels = URLEncoder.encode(Labels.FLAKY_TEST + "," + Labels.AREA_CI + "," + Labels.KIND_BUG, StandardCharsets.UTF_8);

            body.append("[Report flaky test](");
            body.append(repositoryUrl);
            body.append("/issues/new");
            body.append("?title=");
            body.append(issueTitle);
            body.append("&labels=");
            body.append(issueLabels);
            body.append("&body=");
            body.append(issueBody);
            body.append(")\n");
        }

        return body.toString();
    }

}
