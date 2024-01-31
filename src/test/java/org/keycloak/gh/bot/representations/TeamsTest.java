package org.keycloak.gh.bot.representations;

import org.junit.jupiter.api.*;

import java.io.IOException;

public class TeamsTest {

    @BeforeEach
    @AfterEach
    public void resetTeams() {
        Teams.clearInstance();
    }

    @Test
    public void testRemote() throws IOException {
        Teams teams = Teams.getTeams();
        Assertions.assertFalse(teams.isEmpty());
        Assertions.assertFalse(teams.values().iterator().next().isEmpty());
    }

    @Test
    public void testNotPrefixedRemoved() throws IOException {
        Teams teams = Teams.getTeams(getClass().getResource("teams.yml"));
        Assertions.assertFalse(teams.isEmpty());
        Assertions.assertFalse(teams.containsKey("no-team"));
        Assertions.assertTrue(teams.containsKey("team/core-shared"));
    }

}
