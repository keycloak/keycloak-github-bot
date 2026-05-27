package org.keycloak.gh.bot.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommitUtils {

    private static final Pattern LINKED_ISSUE_PATTERN = Pattern.compile("(([c|C]loses)|([f|F]ixes)|([r|R]esolves)) #([0-9]*)");

    public static Integer getIssuerNumber(String commitMessage) {
        Matcher matcher = LINKED_ISSUE_PATTERN.matcher(commitMessage);
        if (matcher.find() && matcher.groupCount() == 5) {
            try {
                return Integer.valueOf(matcher.group(5));
            } catch (NumberFormatException e) {
            }
        }
        return null;
    }

}
