package org.keycloak.gh.bot;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class AddTeamLabelToIssuesTest {

    @Test
    public void testTeamAdded() throws IOException {
        verifyLabelAdded("area/oidc", "team/core-clients");
        verifyLabelAdded("area/organizations", "team/core-iam");
    }

    @Test
    public void notAnArea() throws IOException {
        verifyLabelAdded("kind/bug", null);
    }

    @Test
    public void noTeamMapping() throws IOException {
        verifyLabelAdded("area/nosuch-area", null);
    }

    private void verifyLabelAdded(String areaLabel, String expectedTeam) throws IOException {
        AddTeamLabelToIssues addTeamLabelToIssues = new AddTeamLabelToIssues();

        GHEventPayload.Issue payload = mock(GHEventPayload.Issue.class);
        GHIssue issue = mock(GHIssue.class);
        when(payload.getIssue()).thenReturn(issue);

        GHLabel label = mock(GHLabel.class);
        when(payload.getLabel()).thenReturn(label);

        when(label.getName()).thenReturn(areaLabel);

        addTeamLabelToIssues.onOpen(payload);

        if (expectedTeam != null) {
            verify(issue).addLabels(expectedTeam);
        }
        verifyNoMoreInteractions(issue);
    }

}
