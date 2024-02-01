package org.keycloak.gh.bot.representations;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
        for (Map.Entry<String, List<String>> e : teams.entrySet()) {
            Assertions.assertNotNull(e.getValue());
        }
    }

    @Test
    public void testNotPrefixedRemoved() throws IOException {
        Teams teams = Teams.getTeams(getClass().getResource("teams.yml"));
        Assertions.assertEquals(3, teams.size());

        Assertions.assertTrue(teams.get("team/empty").isEmpty());
        Assertions.assertFalse(teams.containsKey("no-team"));
        MatcherAssert.assertThat(teams.get("team/team-a"), CoreMatchers.hasItems("area/test1"));
        MatcherAssert.assertThat(teams.get("team/team-b"), CoreMatchers.hasItems("area/test2"));
        Assertions.assertTrue(teams.containsKey("team/team-b"));
    }

}
