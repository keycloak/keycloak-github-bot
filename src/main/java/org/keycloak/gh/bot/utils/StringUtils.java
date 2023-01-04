package org.keycloak.gh.bot.utils;

public class StringUtils {

    private static final String TRIM_SUFFIX = "\n...";

    public static String trimLines(String input, int maxLines, boolean addTrimSuffix) {
        int l = 0;
        for (int i = 0; i < maxLines; i++) {
            l = input.indexOf('\n', l + 1);
            if (l == -1) {
                return input;
            }
        }
        String trimmed = input.substring(0, l);
        if (addTrimSuffix && input.length() > trimmed.length()) {
            trimmed += TRIM_SUFFIX;
        }
        return trimmed;
    }

}
