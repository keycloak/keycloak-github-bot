package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FlakyTestParserTest {

    @Test
    public void test() {
        InputStream is = FlakyTestParserTest.class.getResourceAsStream("flaky-tests-tests.zip");
        List<FlakyTestParser.FlakyTest> flakyTests = FlakyTestParser.parse(is);

        assertFlaky(flakyTests.get(0), "playground.stianst.github.io.MyFlakyTest", "flaky2", "java.lang.AssertionError\n\tat playground.stianst.github.io.MyFlakyTest.flaky2(MyFlakyTest.java:22)", 2);
        assertFlaky(flakyTests.get(1), "playground.stianst.github.io.MyFlakyTest", "flaky", "java.lang.AssertionError\n\tat playground.stianst.github.io.MyFlakyTest.flaky(MyFlakyTest.java:15)", 1);
        assertFlaky(flakyTests.get(2), "playground.stianst.github.io.MyVeryFlakyTest", "flaky", "java.lang.AssertionError\n\tat playground.stianst.github.io.MyVeryFlakyTest.flaky(MyVeryFlakyTest.java:15)", 2);
    }

    private void assertFlaky(FlakyTestParser.FlakyTest flakyTest, String expectedClassName, String expectedMethodName, String expectedFailure, int expectedFailureRepeats) {
        assertEquals(expectedClassName, flakyTest.getClassName());
        assertEquals(expectedMethodName, flakyTest.getMethodName());
        assertEquals(expectedFailureRepeats, flakyTest.getFailures().size());
        for (String f : flakyTest.getFailures()) {
            assertEquals(expectedFailure, f);
        }
    }

}
