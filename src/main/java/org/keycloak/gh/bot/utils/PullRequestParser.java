package org.keycloak.gh.bot.utils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PullRequestParser {

    private static final Pattern CLOSING_REFERENCE = Pattern.compile(
            "(?i)\\b(?:close[sd]?|fix(?:es|ed)?|resolve[sd]?)\\s*:?\\s+"
                    + "(?:([\\w.-]+/[\\w.-]+)?#(\\d+)|https://github\\.com/([\\w.-]+/[\\w.-]+)/issues/(\\d+))"
                    + "(?![\\w/])");

    public static Set<Integer> closingIssues(String body, String repository) {
        Set<Integer> issues = new LinkedHashSet<>();
        if (body == null) {
            return issues;
        }
        Matcher matcher = CLOSING_REFERENCE.matcher(body);
        while (matcher.find()) {
            // Groups 1/2 are shorthand references; groups 3/4 are issue URLs.
            String referenceRepository = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            if (referenceRepository != null && !referenceRepository.equalsIgnoreCase(repository)) {
                continue;
            }
            String number = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            try {
                int issueNumber = Integer.parseInt(number);
                if (issueNumber > 0) {
                    issues.add(issueNumber);
                }
            } catch (NumberFormatException e) {
                // Ignore issue numbers outside the client's integer range.
            }
        }
        return issues;
    }
}
