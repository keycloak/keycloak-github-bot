package org.keycloak.gh.bot.representations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class Teams extends HashMap<String, List<String>> {

    private static final String TEAMS_URL = "https://raw.githubusercontent.com/keycloak/keycloak/main/.github/teams.yml";

    private static final long EXPIRATION = 60 * 60 * 1000;

    private static Teams teams;
    private static long lastUpdated;

    private static final Logger log = Logger.getLogger(Teams.class);

    public synchronized static Teams getTeams() throws IOException {
        return getTeams(new URL(TEAMS_URL));
    }

    public synchronized static Teams getTeams(URL url) throws IOException {
        if (teams == null || lastUpdated + EXPIRATION < System.currentTimeMillis()) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            teams = yamlMapper.readValue(url, Teams.class);

            Iterator<Entry<String, List<String>>> itr = teams.entrySet().iterator();
            while (itr.hasNext()) {
                Entry<String, List<String>> e = itr.next();
                if (!e.getKey().startsWith("team/")) {
                    itr.remove();
                } else if (e.getValue() == null) {
                    e.setValue(Collections.EMPTY_LIST);
                }
            }

            lastUpdated = System.currentTimeMillis();
            log.infov("Updating teams list from {0}", TEAMS_URL);
        }
        return teams;
    }

    public synchronized static void clearInstance() {
        teams = null;
        lastUpdated = 0;
    }

    public Teams() {
    }

}
