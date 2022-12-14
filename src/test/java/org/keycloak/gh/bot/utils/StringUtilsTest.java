package org.keycloak.gh.bot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringUtilsTest {

    @Test
    public void lines() {
        String input = "line 1\nline 2\nline 3\nline 4";

        assertEquals("line 1", StringUtils.trimLines(input, 1, false));
        assertEquals("line 1\nline 2\nline 3", StringUtils.trimLines(input, 3, false));
        assertEquals("line 1\nline 2\nline 3\nline 4", StringUtils.trimLines(input, 5, false));

        assertEquals("line 1\n...", StringUtils.trimLines(input, 1, true));
        assertEquals("line 1\nline 2\nline 3\n...", StringUtils.trimLines(input, 3, true));
        assertEquals("line 1\nline 2\nline 3\nline 4", StringUtils.trimLines(input, 5, true));
    }

}
