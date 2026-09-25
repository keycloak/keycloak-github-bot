package org.keycloak.gh.bot.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IssueParser {

    public static String AREA_PREFIX = "area/";

    static final Pattern AREA_PATTERN = Pattern.compile("### Area\\W*([a-z/-]+)");

    public enum TemplateType {
        BUG,
        ENHANCEMENT_OR_FEATURE
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
     * Enhancement and feature templates share identical headers, so they are grouped together.
     */
    public static TemplateType detectTemplateType(String body) {
        if (body == null) {
            return null;
        }
        if (body.contains("### Describe the bug")
                && body.contains("### Expected behavior")
                && body.contains("### Actual behavior")) {
            return TemplateType.BUG;
        }
        if (body.contains("### Value Proposition")
                && body.contains("### Goals")
                && body.contains("### Non-Goals")) {
            return TemplateType.ENHANCEMENT_OR_FEATURE;
        }
        return null;
    }

}
