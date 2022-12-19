package org.keycloak.gh.bot.utils;

import org.jboss.logging.Logger;
import org.keycloak.gh.bot.AddAreaLabelToBugs;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;

public class Labels {

    private static final Logger logger = Logger.getLogger(AddAreaLabelToBugs.class);

    public static final String KIND_BUG = "kind/bug";

    public static final String STATUS_TRIAGE = "status/triage";

    public static final String AREA_CI = "area/ci";

    public static final String FLAKY_TEST = "flaky-test";

    public static boolean hasLabel(GHIssue issue, String label) {
        return issue.getLabels().stream().filter(l -> l.getName().equals(label)).findFirst().isPresent();
    }

    public static void addArea(GHIssue issue, String areaLabel) throws IOException {
        if (!hasLabel(issue, areaLabel)) {
            if (hasLabel(issue.getRepository(), areaLabel)) {
                issue.addLabels(areaLabel);
                logger.infov("Added label " + areaLabel + " to issue '" + issue.getTitle() + "' #" + issue.getNumber());
            } else {
                logger.errorv("Label " + areaLabel + " not found for issue '" + issue.getTitle() + "' #" + issue.getNumber());
            }
        }
    }

    public static void removeLabel(GHIssue issue, String label) throws IOException {
        if (hasLabel(issue, label)) {
            issue.removeLabel(label);
        }
    }

    private static boolean hasLabel(GHRepository repository, String label) throws IOException {
        PagedIterator<GHLabel> itr = repository.listLabels().withPageSize(100).iterator();
        while (itr.hasNext()) {
            if (itr.next().getName().equals(label)) {
                return true;
            }
        }
        return false;
    }
}
