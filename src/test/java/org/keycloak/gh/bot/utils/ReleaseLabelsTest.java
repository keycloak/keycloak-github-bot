package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class ReleaseLabelsTest {

    private static final String REPO = "keycloak/keycloak";

    @Test
    public void mainBranch() {
        ReleaseLabels.Result r = ReleaseLabels.forBaseBranch("main", List.of("main", "release/9.9", "release/26.4", "release/26.10", "release/25.0"), List.of());
        Assertions.assertEquals("release/26.11.0", r.releaseLabel());
        Assertions.assertEquals("backport/main", r.backportLabel());
    }

    @Test
    public void mainBranchMajorBump() {
        ReleaseLabels.Result r = ReleaseLabels.forBaseBranch("main", List.of("main", "release/26.8"), List.of());
        Assertions.assertEquals("release/27.0.0", r.releaseLabel());
    }

    @Test
    public void releaseBranch() {
        ReleaseLabels.Result r = ReleaseLabels.forBaseBranch("release/26.4", List.of(), List.of("26.4.0", "26.4.9", "26.4.10", "26.40.11", "26.5.3"));
        Assertions.assertEquals("release/26.4.11", r.releaseLabel());
        Assertions.assertEquals("backport/26.4", r.backportLabel());
    }

    @Test
    public void otherBranch() {
        Assertions.assertNull(ReleaseLabels.forBaseBranch("feature/x", List.of("release/26.4"), List.of()));
    }

    // Same cases as .github/scripts/pr-find-issues-test.sh in keycloak/keycloak
    @Test
    public void issueReferenceVariants() {
        for (String body : List.of(
                "Closes #123",
                "Fixes #123",
                "Fixes: #123",
                "Fixes: keycloak/keycloak#123",
                "Fixes https://github.com/keycloak/keycloak/issues/123")) {
            Assertions.assertEquals(Set.of(123), ReleaseLabels.findIssues(REPO, body, null), body);
        }
    }

    @Test
    public void issueReferenceEdgeCases() {
        Assertions.assertEquals(Set.of(123), ReleaseLabels.findIssues(REPO, "Fix #123", null));
        Assertions.assertEquals(Set.of(), ReleaseLabels.findIssues(REPO, "Discloses #123", null));
        Assertions.assertEquals(Set.of(7), ReleaseLabels.findIssues(REPO, "Closes #2147483648\nCloses #7", null));
    }

    @Test
    public void issuesFromBodyOrCommit() {
        Assertions.assertEquals(Set.of(12, 34),
                ReleaseLabels.findIssues(REPO, "Closes #12\nfixes: https://github.com/keycloak/keycloak/issues/34\nCloses other/repo#5", "Closes #99"));
        Assertions.assertEquals(Set.of(99), ReleaseLabels.findIssues(REPO, "nothing", "Closes #99"));
        Assertions.assertEquals(Set.of(), ReleaseLabels.findIssues(REPO, null, null));
    }

}
