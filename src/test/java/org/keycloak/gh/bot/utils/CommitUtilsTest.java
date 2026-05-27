package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CommitUtilsTest {

    @Test
    public void testCloses() {
        Assertions.assertEquals(1232456, CommitUtils.getIssuerNumber("This is some commit message\nCloses #1232456 and something else"));
        Assertions.assertEquals(1232456, CommitUtils.getIssuerNumber("This is some commit message\ncloses #1232456 and something else"));
        Assertions.assertEquals(1232456, CommitUtils.getIssuerNumber("This is some commit message\nFixes #1232456 and something else\nSomething else"));
        Assertions.assertEquals(1232456, CommitUtils.getIssuerNumber("This is some commit message\nfixes #1232456 and something else\nSomething else"));
        Assertions.assertEquals(1232456, CommitUtils.getIssuerNumber("Resolves #1232456 and something else\nSomething else"));
        Assertions.assertEquals(1232456, CommitUtils.getIssuerNumber("resolves #1232456 and something else\nSomething else"));
        Assertions.assertNull(CommitUtils.getIssuerNumber("Some message\nResolves 1232456"));
        Assertions.assertNull(CommitUtils.getIssuerNumber("Some message\nResolves: #1232456"));
    }

}
