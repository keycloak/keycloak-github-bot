package org.keycloak.gh.bot.security.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.GitHubInstallationProvider;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommandParserTest {

    private CommandParser parser;

    @BeforeEach
    public void setup() {
        parser = new CommandParser();
        parser.gitHubProvider = mock(GitHubInstallationProvider.class);
        when(parser.gitHubProvider.getBotLogin()).thenReturn("keycloak-bot");
        parser.init();
    }

    @Test
    public void testNewSecAlertValid() {
        String input = "@keycloak-bot /new secalert \"RCE in Admin Console\"\n\nFound a vulnerability.";
        Optional<CommandParser.Command> cmd = parser.parse(input);

        assertTrue(cmd.isPresent());
        assertEquals(CommandParser.CommandType.NEW_SECALERT, cmd.get().type());
        assertEquals("RCE in Admin Console", cmd.get().subject().get());
        assertEquals("Found a vulnerability.", cmd.get().body());
    }

    @Test
    public void testReplyValid() {
        String input = "@keycloak-bot /reply keycloak-security\n\nThis is a reply";
        Optional<CommandParser.Command> cmd = parser.parse(input);

        assertTrue(cmd.isPresent());
        assertEquals(CommandParser.CommandType.REPLY_KEYCLOAK_SECURITY, cmd.get().type());
        assertEquals("This is a reply", cmd.get().body());
    }

    @Test
    public void testReplyWithJunkOnCommandLineFails() {
        String input = "@keycloak-bot /reply keycloak-security meh";
        Optional<CommandParser.Command> cmd = parser.parse(input);

        assertTrue(cmd.isPresent());
        assertEquals(CommandParser.CommandType.UNKNOWN, cmd.get().type());
    }

    @Test
    public void testNewSecAlertWithJunkOnCommandLineFails() {
        String input = "@keycloak-bot /new secalert \"Subject\" meh";
        Optional<CommandParser.Command> cmd = parser.parse(input);

        assertTrue(cmd.isPresent());
        assertEquals(CommandParser.CommandType.UNKNOWN, cmd.get().type());
    }

    @Test
    public void testIgnoreNonMention() {
        assertTrue(parser.parse("No mention here").isEmpty());
    }
}