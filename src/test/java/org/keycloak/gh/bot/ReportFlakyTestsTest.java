package org.keycloak.gh.bot;

import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.utils.FlakyTestParser;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHWorkflowRun;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReportFlakyTestsTest {

    @Test
    public void title() {
        ReportFlakyTests reportFlakyTests = new ReportFlakyTests();

        FlakyTestParser.FlakyTest flakyTest = new FlakyTestParser.FlakyTest("my.package.MyClass", "myTest");
        assertEquals("Flaky test: my.package.MyClass#myTest", reportFlakyTests.getTitle(flakyTest));
    }

    @Test
    public void bodySchedule() throws IOException {
        ReportFlakyTests reportFlakyTests = new ReportFlakyTests();

        FlakyTestParser.FlakyTest flakyTest = new FlakyTestParser.FlakyTest("my.package.MyClass", "myTest");
        flakyTest.addFailure("Failure 1\n\tSecond line");
        flakyTest.addFailure("Failure 2");

        String expectedBody = new String(ReportFlakyTestsTest.class.getResourceAsStream("flaky-test-body-schedule").readAllBytes(), StandardCharsets.UTF_8);

        GHWorkflowRun workflowRun = mock(GHWorkflowRun.class);
        when(workflowRun.getHtmlUrl()).thenReturn(new URL("https://workflow-link"));
        when(workflowRun.getEvent()).thenReturn(GHEvent.SCHEDULE);

        assertEquals(expectedBody, reportFlakyTests.getBody(flakyTest, workflowRun, null));
    }

    @Test
    public void bodyPullRequest() throws IOException {
        ReportFlakyTests reportFlakyTests = new ReportFlakyTests();

        FlakyTestParser.FlakyTest flakyTest = new FlakyTestParser.FlakyTest("my.package.MyClass", "myTest");
        flakyTest.addFailure("Failure 1\n\tSecond line");
        flakyTest.addFailure("Failure 2");

        String expectedBody = new String(ReportFlakyTestsTest.class.getResourceAsStream("flaky-test-body-pull_request").readAllBytes(), StandardCharsets.UTF_8);

        GHWorkflowRun workflowRun = mock(GHWorkflowRun.class);

        when(workflowRun.getHtmlUrl()).thenReturn(new URL("https://workflow-link"));
        when(workflowRun.getEvent()).thenReturn(GHEvent.PULL_REQUEST);

        GHPullRequest pullRequest = mock(GHPullRequest.class);
        when(pullRequest.getHtmlUrl()).thenReturn(new URL("https://pull-request-link/123"));

        assertEquals(expectedBody, reportFlakyTests.getBody(flakyTest, workflowRun, pullRequest));
    }

}
