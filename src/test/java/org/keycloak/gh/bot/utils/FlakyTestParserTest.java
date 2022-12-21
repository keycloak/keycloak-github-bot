package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlakyTestParserTest {

    @Test
    public void test2() {
        InputStream is = FlakyTestParserTest.class.getResourceAsStream("flaky-tests-base-integration-tests.zip");
        List<FlakyTestParser.FlakyTest> flakyTests = FlakyTestParser.parse(is);

        assertEquals(1, flakyTests.size());
        assertFlaky(flakyTests.get(0), "org.keycloak.testsuite.cookies.CookiesPathTest", "testOldCookieWithWrongPath", "java.lang.AssertionError: \n" +
                "\n" +
                "Expected: a collection with size <3>\n" +
                "     but: collection size was <2>\n" +
                "\tat org.hamcrest.MatcherAssert.assertThat(MatcherAssert.java:20)\n", true, 1);
    }

    @Test
    public void test() {
        InputStream is = FlakyTestParserTest.class.getResourceAsStream("flaky-tests-tests.zip");
        List<FlakyTestParser.FlakyTest> flakyTests = FlakyTestParser.parse(is);

        assertFlaky(flakyTests.get(0), "playground.stianst.github.io.MyFlakyTest", "flaky2", "java.lang.AssertionError\n\tat playground.stianst.github.io.MyFlakyTest.flaky2(MyFlakyTest.java:22)", false,2);
        assertFlaky(flakyTests.get(1), "playground.stianst.github.io.MyFlakyTest", "flaky", "java.lang.AssertionError\n\tat playground.stianst.github.io.MyFlakyTest.flaky(MyFlakyTest.java:15)", false, 1);
        assertFlaky(flakyTests.get(2), "playground.stianst.github.io.MyVeryFlakyTest", "flaky", "java.lang.AssertionError\n\tat playground.stianst.github.io.MyVeryFlakyTest.flaky(MyVeryFlakyTest.java:15)", false, 2);
    }

    private void assertFlaky(FlakyTestParser.FlakyTest flakyTest, String expectedClassName, String expectedMethodName, String expectedFailure, boolean startsWith, int expectedFailureRepeats) {
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
    }

}
