package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FlakyJobParserTest {

    @Test
    public void testPr() throws IOException {
        InputStream is = FlakyJobParserTest.class.getResourceAsStream("flaky-tests-pr.zip");
        FlakyJob flakyJob = FlakyJobParser.parse(is);
        flakyJob.setWorkflow("Keycloak CI");

        List<FlakyTest> flakyTests = flakyJob.getFlakyTests();

        assertEquals("Base IT (1)", flakyJob.getJobName());
        assertEquals("https://github.com/stianst/playground/actions/runs/3821978574/jobs/6501658733", flakyJob.getJobUrl());
        assertEquals("65", flakyJob.getPr());
        assertEquals("https://github.com/stianst/playground/pull/65", flakyJob.getPrUrl());

        assertEquals(1, flakyTests.size());
        assertFlaky(flakyTests.get(0), "playground.stianst.github.io.MyFlakyTest", "flaky", "java.lang.AssertionError: expected:<1> but was:<0>\n" +
                "\tat playground.stianst.github.io.MyFlakyTest.flaky(MyFlakyTest.java:11)", true, 1);
    }

    @Test
    public void testPush() throws IOException {
        InputStream is = FlakyJobParserTest.class.getResourceAsStream("flaky-tests-push.zip");
        FlakyJob flakyJob = FlakyJobParser.parse(is);
        flakyJob.setWorkflow("Keycloak CI");

        List<FlakyTest> flakyTests = flakyJob.getFlakyTests();

        assertEquals("Unit Tests", flakyJob.getJobName());
        assertEquals("https://github.com/stianst/playground/actions/runs/3821978350/jobs/6501658170", flakyJob.getJobUrl());
        assertNull(flakyJob.getPr());
        assertNull(flakyJob.getPrUrl());

        assertEquals(1, flakyTests.size());
        assertFlaky(flakyTests.get(0), "playground.stianst.github.io.MyOtherFlakyTest", "otherFlaky", "java.lang.AssertionError: expected:<1> but was:<0>\n" +
                "\tat playground.stianst.github.io.MyOtherFlakyTest.otherFlaky(MyOtherFlakyTest.java:1)\n" +
                "\tat playground.stianst.github.io.MyOtherFlakyTest.otherFlaky(MyOtherFlakyTest.java:2)\n" +
                "\tat playground.stianst.github.io.MyOtherFlakyTest.otherFlaky(MyOtherFlakyTest.java:3)\n" +
                "\tat playground.stianst.github.io.MyOtherFlakyTest.otherFlaky(MyOtherFlakyTest.java:4)", true, 1);
    }

    private void assertFlaky(FlakyTest flakyTest, String expectedClassName, String expectedMethodName, String expectedFailure, boolean startsWith, int expectedFailureRepeats) throws IOException {
        assertEquals(expectedClassName, flakyTest.getClassName());
        assertEquals(expectedMethodName, flakyTest.getMethodName());
        assertEquals(expectedFailureRepeats, flakyTest.getFailures().size());
        for (String f : flakyTest.getFailures()) {
            if (startsWith) {
                assertThat(f, startsWith(expectedFailure));
            } else {
                assertEquals(expectedFailure, f);
            }
        }
        assertIssueTitle(flakyTest, expectedClassName, expectedMethodName);
        assertIssueBody(flakyTest);
    }

    private void assertIssueTitle(FlakyTest flakyTest, String expectedClassName, String expectedMethodName) {
        assertEquals("Flaky test: " + expectedClassName + "#" + expectedMethodName, flakyTest.getIssueTitle());
    }

    private void assertIssueBody(FlakyTest flakyTest) throws IOException {
        String expectedBody = new String(FlakyJobParserTest.class.getResourceAsStream("flaky-test-issue-body--" + flakyTest.getClassName() + "--" + flakyTest.getMethodName()).readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(expectedBody, flakyTest.getIssueBody());
    }

}
