package org.keycloak.gh.bot.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.email.CommandParser.Command;
import org.keycloak.gh.bot.email.CommandParser.CommandType;

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
        parser.gitHubProvider = mock(org.keycloak.gh.bot.GitHubInstallationProvider.class);
        when(parser.gitHubProvider.getBotLogin()).thenReturn("keycloak-bot");
    }

    @Test
    public void testReplyParsing() {
        String text = """
                @keycloak-bot /reply keycloak-security
                This is the body.
                Multiple lines.
                """;

        Optional<Command> cmd = parser.parse(text);

        assertTrue(cmd.isPresent());
        assertEquals(CommandType.REPLY_KEYCLOAK_SECURITY, cmd.get().type());
        assertEquals("This is the body.\nMultiple lines.", cmd.get().body());
    }

    @Test
    public void testNewSecAlertParsing() {
        String text = """
                @keycloak-bot /new secalert "New CVE"
                Details here.
                """;

        Optional<Command> cmd = parser.parse(text);

        assertTrue(cmd.isPresent());
        assertEquals(CommandType.NEW_SECALERT, cmd.get().type());
        assertEquals("New CVE", cmd.get().subject().get());
        assertEquals("Details here.", cmd.get().body());
    }

    @Test
    public void testUnknownCommandParsing() {
        String text = "@keycloak-bot /unknown command";

        Optional<Command> cmd = parser.parse(text);

        assertTrue(cmd.isPresent());
        assertEquals(CommandType.UNKNOWN, cmd.get().type());
    }

    @Test
    public void testIgnoreNonMention() {
        String text = "Just a comment without bot mention.";
        Optional<Command> cmd = parser.parse(text);
        assertTrue(cmd.isEmpty());
    }
}