package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.WorkflowRun;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.utils.FlakyTestParser;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.List;

public class ReportFlakyTests {

    Logger logger = Logger.getLogger(ReportFlakyTests.class);

    void onCompleted(@WorkflowRun.Completed GHEventPayload.WorkflowRun workflowRun, GitHub gitHub) throws IOException {
        PagedIterator<GHArtifact> iterator = workflowRun.getWorkflowRun().listArtifacts().iterator();
        while (iterator.hasNext()) {
            GHArtifact ghArtifact = iterator.next();
            if (ghArtifact.getName().startsWith("flaky-tests-")) {
                List<FlakyTestParser.FlakyTest> flakyTests = ghArtifact.download(inputStream -> FlakyTestParser.parse(inputStream));

                for (FlakyTestParser.FlakyTest flakyTest : flakyTests) {
                    GHIssue issue = findIssue(gitHub, flakyTest);
                    if (issue != null) {
                        String body = getBody(flakyTest, workflowRun.getWorkflowRun().getHtmlUrl().toString());
                        issue.comment(body);

                        logger.infov("Added comment to existing issue {0}", issue.getHtmlUrl());
                    } else {
                        issue = createIssue(flakyTest, workflowRun.getWorkflowRun());

                        logger.infov("Created issue {0}", issue.getHtmlUrl());
                    }
                }
            }
        }
    }

    public GHIssue findIssue(GitHub gitHub, FlakyTestParser.FlakyTest flakyTest) throws IOException {
        String title = getTitle(flakyTest);
        List<GHIssue> issues = gitHub.searchIssues().isOpen().q("label:" + Labels.FLAKY_TEST + " in:title " + title).list().withPageSize(1).toList();
        return !issues.isEmpty() ? issues.get(0) : null;
    }

    public GHIssue createIssue(FlakyTestParser.FlakyTest flakyTest, GHWorkflowRun workflowRun) throws IOException {
        String title = getTitle(flakyTest);
        String body = getBody(flakyTest, workflowRun.getHtmlUrl().toString());
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

    public String getBody(FlakyTestParser.FlakyTest flakyTest, String workflowRunUrl) {
        StringBuilder body = new StringBuilder();

        body.append("## ");
        body.append(flakyTest.getClassName());
        body.append("#");
        body.append(flakyTest.getMethodName());
        body.append("\n");

        body.append(workflowRunUrl);
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
