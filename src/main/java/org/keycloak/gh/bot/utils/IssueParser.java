package org.keycloak.gh.bot.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IssueParser {

    public static String AREA_PREFIX = "area/";

    static final Pattern AREA_PATTERN = Pattern.compile("### Area\\W*([a-z/-]+)");

    public enum TemplateType {
        BUG,
        ENHANCEMENT,
        FEATURE,
        TASK
    }

    public static String getAreaFromBody(String body) {
        Matcher matcher = AREA_PATTERN.matcher(body);
        if (matcher.find()) {
            return AREA_PREFIX + matcher.group(1);
        } else {
            return null;
        }
    }

    /**
     * Detects the issue template type from the body structure.
     * Supports the templates from before and after keycloak/keycloak#55144 (merged 2026-11-13).
     */
    public static TemplateType detectTemplateType(String body) {
        if (body == null) {
            return null;
        }

        // Templates after keycloak/keycloak#55144: each type has its own description header.
        if (hasHeading(body, "Bug Description")) {
            return TemplateType.BUG;
        }
        if (hasHeading(body, "Enhancement Description")) {
            return TemplateType.ENHANCEMENT;
        }
        if (hasHeading(body, "Feature Request Description")) {
            return TemplateType.FEATURE;
        }
        if (hasHeading(body, "Task Description")) {
            return TemplateType.TASK;
        }

        // Templates before keycloak/keycloak#55144: the bug template used different headers,
        // and the enhancement and feature templates were identical, so they can't be told apart.
        // Assume an enhancement, the triager can change this to a feature later.
        // Agents working on old forks might still use the old way.
        if (hasHeading(body, "Describe the bug")
                && hasHeading(body, "Expected behavior")
                && hasHeading(body, "Actual behavior")) {
            return TemplateType.BUG;
        }
        if (hasHeading(body, "Value Proposition")
                && hasHeading(body, "Goals")
                && hasHeading(body, "Non-Goals")) {
            return TemplateType.ENHANCEMENT;
        }
        return null;
    }

    /**
     * Matches only a heading on a line of its own, so text that mentions or quotes a heading is ignored.
     * Headings inside fenced code blocks still match; this is an accepted limitation.
     */
    private static boolean hasHeading(String body, String title) {
        return body.lines().anyMatch(line -> line.strip().equals("### " + title));
    }

}
