package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.GitHubEvent;
import io.quarkiverse.githubapp.event.RawEvent;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SyncKindLabels {

    private static final Logger logger = Logger.getLogger(SyncKindLabels.class);

    void onTyped(@RawEvent(event = "issues", action = "typed") GitHubEvent event, GitHub gitHub) throws IOException {
        onTypeChange(event, true, gitHub);
    }

    void onUntyped(@RawEvent(event = "issues", action = "untyped") GitHubEvent event, GitHub gitHub) throws IOException {
        onTypeChange(event, false, gitHub);
    }

    void onTypeChange(GitHubEvent event, boolean typed, GitHub gitHub) throws IOException {
        JsonObject payload = event.getParsedPayload();

        JsonObject type = payload.getJsonObject("type");
        String typeName = type != null ? type.getString("name") : null;
        String labelName = "kind/" + typeName;

        JsonObject issue = payload.getJsonObject("issue");
        int issueNumber = issue.getInteger("number");

        JsonArray labels = issue.getJsonArray("labels");
        List<String> kindLabels;
        if (labels != null) {
            kindLabels = labels.stream().map(o -> (JsonObject) o).map(o -> o.getString("name")).filter(l -> l.startsWith("kind/")).toList();
        } else {
            kindLabels = Collections.emptyList();
        }

        String labelToAdd = null;
        List<String> labelsToRemove;

        if (typed) {
            if (!kindLabels.contains(labelName)) {
                labelToAdd = labelName;
            }
            labelsToRemove = kindLabels.stream().filter(l -> !l.equals(labelName)).toList();
        } else {
            labelsToRemove = kindLabels;
        }

        if (labelToAdd != null || !labelsToRemove.isEmpty()) {
            logger.infov("issue={0}, typed={1}, labelToAdd={2}, labelsToRemove={3}", issueNumber, typed, labelToAdd, labelsToRemove);

            GHIssue ghIssue = gitHub.getRepository(event.getRepositoryOrThrow()).getIssue(issue.getInteger("number"));
            if (labelToAdd != null) {
                ghIssue.addLabels(labelToAdd);
            }
            if (!labelsToRemove.isEmpty()) {
                ghIssue.removeLabels(labelsToRemove.toArray(new String[0]));
            }
        }
    }

}
