package org.keycloak.gh.bot.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IssueParser {

    public static String AREA_PREFIX = "area/";

    static final Pattern AREA_PATTERN = Pattern.compile("### Area\\W*([a-z/-]+)");

    public static String getAreaFromBody(String body) {
        Matcher matcher = AREA_PATTERN.matcher(body);
        if (matcher.find()) {
            return AREA_PREFIX + matcher.group(1);
        } else {
            return null;
        }
    }

    public static boolean isRegression(String body) {
        return body.contains("- [X] The issue is a regressions");
    }

}
