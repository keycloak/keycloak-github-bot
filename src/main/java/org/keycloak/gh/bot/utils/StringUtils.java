package org.keycloak.gh.bot.utils;

public class StringUtils {

    public static String trimLines(String input, int maxLines) {
        int l = 0;
        for (int i = 0; i < maxLines; i++) {
            l = input.indexOf('\n', l + 1);
            if (l == -1) {
                return input;
            }
        }
        return input.substring(0, l);
    }

}
