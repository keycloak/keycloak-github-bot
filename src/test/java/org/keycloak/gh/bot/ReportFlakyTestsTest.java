package org.keycloak.gh.bot;

import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.utils.FlakyJob;
import org.keycloak.gh.bot.utils.FlakyJobParser;
import org.keycloak.gh.bot.utils.FlakyTest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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

    @Test
    void parseFlakySurefireReports() {
        // Parse reports from zip file
        InputStream surefireReportsStream = ReportFlakyTestsTest.class.getResourceAsStream("flaky-test-surefire-reports/flaky-test-surefire-reports.zip");
        FlakyJob flakyJob = FlakyJobParser.parse(surefireReportsStream);

        // Assert values from job-summary.properties file
        assertThat(flakyJob.getWorkflow(), nullValue());
        assertThat(flakyJob.getJobName(), equalTo("Store Model Tests"));
        assertThat(flakyJob.getJobUrl(), equalTo("https://github.com/keycloak/keycloak/actions/runs/5932783826/job/16087537063"));

        // Get sorted values of flaky tests (we need to sort to assert in the correct order)
        List<FlakyTest> flakyTests = flakyJob.getFlakyTests().stream().sorted(Comparator.comparing(FlakyTest::getMethodName)).collect(Collectors.toList());

        // Assert values from flaky tests surefire report
        assertThat(flakyTests, hasSize(2));

        assertThat(flakyTests.get(0).getClassName(), equalTo("org.keycloak.testsuite.model.FlakyTest"));
        assertThat(flakyTests.get(0).getMethodName(), equalTo("flakyErrorTest"));
        assertThat(flakyTests.get(0).getFailures(), hasSize(2));
        assertThat(flakyTests.get(0).getFailures().get(0), containsString("java.lang.RuntimeException: Flaky runtime exception"));

        assertThat(flakyTests.get(1).getClassName(), equalTo("org.keycloak.testsuite.model.FlakyTest"));
        assertThat(flakyTests.get(1).getMethodName(), equalTo("flakyFailureTest"));
        assertThat(flakyTests.get(1).getFailures(), hasSize(2));
        assertThat(flakyTests.get(1).getFailures().get(0), containsString("java.lang.AssertionError"));
        assertThat(flakyTests.get(1).getFailures().get(0), containsString("at org.junit.Assert.fail(Assert.java:87)"));

    }
}
