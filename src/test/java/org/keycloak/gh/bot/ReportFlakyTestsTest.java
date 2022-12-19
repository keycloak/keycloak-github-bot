package org.keycloak.gh.bot;

import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.utils.FlakyTestParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReportFlakyTestsTest {

    @Test
    public void title() {
        ReportFlakyTests reportFlakyTests = new ReportFlakyTests();

        FlakyTestParser.FlakyTest flakyTest = new FlakyTestParser.FlakyTest("my.package.MyClass", "myTest");
        assertEquals("Flaky test: my.package.MyClass#myTest", reportFlakyTests.getTitle(flakyTest));
    }

    @Test
    public void body() throws IOException {
        ReportFlakyTests reportFlakyTests = new ReportFlakyTests();

        FlakyTestParser.FlakyTest flakyTest = new FlakyTestParser.FlakyTest("my.package.MyClass", "myTest");
        flakyTest.addFailure("Failure 1\n\tSecond line");
        flakyTest.addFailure("Failure 2");

        String expectedBody = new String(ReportFlakyTestsTest.class.getResourceAsStream("flaky-test-body").readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(expectedBody, reportFlakyTests.getBody(flakyTest, "https://workflow-link"));
    }

}
