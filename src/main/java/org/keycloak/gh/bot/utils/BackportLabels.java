package org.keycloak.gh.bot.utils;

import org.kohsuke.github.GHLabel;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

public final class BackportLabels {

    private static final String PREFIX = "backport/";

    private BackportLabels() {
    }

    public static boolean isRequired(String name, Set<String> repositoryBranches) {
        return name.startsWith(PREFIX) && name.length() > PREFIX.length() && !name.equals("backport/main")
                && !repositoryBranches.contains("archive/" + targetBranch(name));
    }

    public static Set<String> requiredBranches(Collection<GHLabel> labels, Set<String> repositoryBranches) {
        Set<String> branches = new TreeSet<>();
        for (GHLabel label : labels) {
            if (isRequired(label.getName(), repositoryBranches)) {
                branches.add(targetBranch(label.getName()));
            }
        }
        return branches;
    }

    private static String targetBranch(String labelName) {
        return "release/" + labelName.substring(PREFIX.length());
    }
}
