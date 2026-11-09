package org.keycloak.gh.bot.utils;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReleaseLabels {

    public record Result(String releaseLabel, String backportLabel) {
    }

    private static final Pattern RELEASE_BRANCH = Pattern.compile("release/(\\d+)\\.(\\d+)");

    /**
     * @return the labels for a PR merged into the given branch, or null if the branch is neither main nor a release branch
     */
    public static Result forBaseBranch(String base, Collection<String> branches, Collection<String> tags) {
        if (base.equals("main")) {
            int[] last = branches.stream()
                    .map(RELEASE_BRANCH::matcher)
                    .filter(Matcher::matches)
                    .map(m -> new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))})
                    .max(Comparator.<int[]>comparingInt(v -> v[0]).thenComparingInt(v -> v[1]))
                    .orElse(null);
            if (last == null) {
                return null;
            }
            String label = "release/" + last[0] + "." + (last[1] + 1) + ".0";
            // There will be no more minor release on the 26.x line; 26.9.0 is actually released as the major bump 27.0.0.
            if (label.equals("release/26.9.0")) {
                label = "release/27.0.0";
            }
            return new Result(label, "backport/main");
        }

        Matcher m = RELEASE_BRANCH.matcher(base);
        if (m.matches()) {
            String majorMinor = m.group(1) + "." + m.group(2);
            Pattern tagPattern = Pattern.compile(Pattern.quote(majorMinor) + "\\.(\\d+)");
            int lastMicro = tags.stream()
                    .map(tagPattern::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(t -> Integer.parseInt(t.group(1)))
                    .max()
                    .orElse(-1);
            return new Result("release/" + majorMinor + "." + (lastMicro + 1), "backport/" + majorMinor);
        }

        return null;
    }

    /**
     * Finds issues closed by the PR description, falling back to the merge commit message if the description has none.
     */
    public static Set<Integer> findIssues(String repository, String prBody, String commitMessage) {
        Set<Integer> issues = PullRequestParser.closingIssues(prBody, repository);
        if (issues.isEmpty()) {
            issues = PullRequestParser.closingIssues(commitMessage, repository);
        }
        return issues;
    }

}
