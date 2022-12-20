package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.WorkflowRun;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.FlakyTestParser;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.List;

public class ReportFlakyTests {

    Logger logger = Logger.getLogger(ReportFlakyTests.class);

    void onCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRunEvent, GitHub gitHub) throws IOException {
        GHWorkflow workflow = workflowRunEvent.getWorkflow();
        GHWorkflowRun workflowRun = workflowRunEvent.getWorkflowRun();

        if(workflow.getName().equals("Keycloak CI")) {
            boolean flaky = false;

            PagedIterator<GHArtifact> iterator = workflowRun.listArtifacts().iterator();
            while (iterator.hasNext()) {
                GHArtifact ghArtifact = iterator.next();
                if (ghArtifact.getName().startsWith("flaky-tests-")) {
                    List<FlakyTestParser.FlakyTest> flakyTests = ghArtifact.download(inputStream -> FlakyTestParser.parse(inputStream));

                    for (FlakyTestParser.FlakyTest flakyTest : flakyTests) {
                        GHIssue issue = findIssue(gitHub, flakyTest);
                        if (issue != null) {
                            String body = getBody(flakyTest, workflowRun);
                            issue.comment(body);

                            logger.infov("Flakes found in {0}, added comment to existing issue {1}", workflowRun.getHtmlUrl(), issue.getHtmlUrl());
                            flaky = true;
                        } else {
                            issue = createIssue(flakyTest, workflowRun);

                            logger.infov("Flakes found in {0}, created issue {1}", workflowRun.getHtmlUrl(), issue.getHtmlUrl());
                            flaky = true;
                        }
                    }
                }
            }
            if (!flaky) {
                logger.infov("No flakes found in {0}", workflowRun.getHtmlUrl());
            }
        } else {
            logger.infov("Skipping {0}", workflowRun.getName());
        }
    }

    public GHIssue findIssue(GitHub gitHub, FlakyTestParser.FlakyTest flakyTest) throws IOException {
        String title = getTitle(flakyTest);
        List<GHIssue> issues = gitHub.searchIssues().isOpen().q("label:" + Labels.FLAKY_TEST + " in:title " + title).list().withPageSize(1).toList();
        return !issues.isEmpty() ? issues.get(0) : null;
    }

    public GHIssue createIssue(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun) throws IOException {
        String title = getTitle(flakyTest);
        String body = getBody(flakyTest, workflowRun);
        GHIssue issue = workflowRun.getRepository()
                .createIssue(title)
                .body(body)
                .label(Labels.KIND_BUG)
                .label(Labels.AREA_CI)
                .label(Labels.FLAKY_TEST)
                .create();
        return issue;
    }

    public String getTitle(FlakyTestParser.FlakyTest flakyTest) {
        return "Flaky test: " + flakyTest.getClassName() + "#" + flakyTest.getMethodName();
    }

    public String getBody(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun) throws IOException {
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

        if (!workflowRun.getPullRequests().isEmpty()) {
            GHPullRequest pullRequest = workflowRun.getPullRequests().get(0);

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

}
