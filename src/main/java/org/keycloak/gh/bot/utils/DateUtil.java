package org.keycloak.gh.bot.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateUtil {

    private static final DateTimeFormatter DATE_STRING_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);

    public static String minusDaysString(long days) {
        return DATE_STRING_FORMATTER.format(LocalDateTime.now().minusDays(days));
    }

}
