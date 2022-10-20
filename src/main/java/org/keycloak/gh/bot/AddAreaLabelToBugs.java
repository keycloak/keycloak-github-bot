package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds 'area/...' label to bugs by using the area selected by the reporter in the area dropdown on the bug issue form.
 */
public class AddAreaLabelToBugs {

    static final Pattern AREA_PATTERN = Pattern.compile("### Area\\W*([a-z/]+)");

    void onOpen(@Issue.Opened GHEventPayload.Issue issuePayload) throws IOException {
        GHIssue issue = issuePayload.getIssue();

        if (Labels.hasLabel(issue, Labels.KIND_BUG)) {
            Matcher matcher = AREA_PATTERN.matcher(issuePayload.getIssue().getBody());
            if (matcher.find()) {
                Labels.addArea(issue, matcher.group(1));
            }
        }
    }

}
