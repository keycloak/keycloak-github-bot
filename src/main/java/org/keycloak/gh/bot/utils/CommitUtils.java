package org.keycloak.gh.bot.utils;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommitUtils {

    private static final Pattern LINKED_ISSUE_PATTERN = Pattern.compile("(([c|C]loses)|([f|F]ixes)|([r|R]esolves)) #([0-9]*)");

    public static Integer getIssuerNumber(String commitMessage) {
        Matcher matcher = LINKED_ISSUE_PATTERN.matcher(commitMessage);
        if (matcher.find() && matcher.groupCount() == 5) {
            try {
                return Integer.valueOf(matcher.group(5));
            } catch (NumberFormatException e) {
            }
        }
        return null;
    }

    public static List<GHIssue> linkedIssues(GHRepository repository, GHPullRequest pullRequest) throws IOException {
        List<GHIssue> ghIssues = new LinkedList<>();
        List<String> commitMessages = pullRequest.listCommits().toList().stream().map(i -> i.getCommit().getMessage()).toList();
        for (String commitMessage : commitMessages) {
            Integer issuerNumber = getIssuerNumber(commitMessage);
            if (issuerNumber != null) {
                try {
                    GHIssue issue = repository.getIssue(issuerNumber);
                    ghIssues.add(issue);
                } catch (IOException e) {
                    // Ignore not found issue number
                }
            }
        }
        return ghIssues;
    }

}
