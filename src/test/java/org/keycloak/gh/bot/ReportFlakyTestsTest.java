package org.keycloak.gh.bot;

import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.utils.FlakyJob;
import org.keycloak.gh.bot.utils.FlakyTest;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReportFlakyTestsTest {

    @Test
    public void pullRequestReviewBody() throws IOException {
        ReportFlakyTests reportFlakyTests = new ReportFlakyTests();

        FlakyJob flakyJob = new FlakyJob();
        flakyJob.setWorkflow("Keycloak CI");
        flakyJob.setJobName("Job Name");
        flakyJob.setJobUrl("https://job-url");

        FlakyTest flakyTest = new FlakyTest(flakyJob, "my.package.MyClass", "myTest");
        flakyTest.addFailure("Failure 1\n\tSecond line");
        flakyTest.addFailure("Failure 2");

        String expectedBody = new String(ReportFlakyTestsTest.class.getResourceAsStream("flaky-test-pull-request-body").readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(expectedBody, reportFlakyTests.getPullRequestReviewBody(Collections.singletonList(flakyTest), new URL("https://github.com/stianst/playground")));
    }

}
