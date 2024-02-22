package org.keycloak.gh.bot;

import io.quarkiverse.githubapp.event.Issue;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.labels.Action;
import org.keycloak.gh.bot.labels.Kind;
import org.keycloak.gh.bot.labels.Label;
import org.keycloak.gh.bot.labels.Priority;
import org.keycloak.gh.bot.labels.Status;
import org.keycloak.gh.bot.utils.Labels;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHIssueStateReason;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHMilestone;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class BugActions {

    private static final Logger logger = Logger.getLogger(BugActions.class);

    private static final Pattern MAJOR_VERSION_PATTERN = Pattern.compile("(\\d+)\\.\\d+.\\d+");

    private static final Map<String, BugAction> actions = initActions(
            BugAction.create(Action.QUESTION)
                    .comment()
                    .closeAsNotPlanned(),
            BugAction.create(Action.MISSING_INFO)
                    .comment()
                    .status(Status.MISSING_INFORMATION)
                    .autoExpire(),
            BugAction.create(Action.OLD_RELEASE)
                    .comment()
                    .status(Status.MISSING_INFORMATION)
                    .autoExpire(),
            BugAction.create(Action.INVALID)
                    .comment()
                    .closeAsNotPlanned(),
            BugAction.create(Action.PRIORITY_REGRESSION)
                    .priority(Priority.BLOCKER)
                    .kind(Kind.REGRESSION)
                    .nextMilestone(),
            BugAction.create(Action.PRIORITY_IMPORTANT)
                    .priority(Priority.IMPORTANT),
            BugAction.create(Action.PRIORITY_NORMAL)
                    .priority(Priority.NORMAL)
                    .comment()
                    .helpWanted()
                    .autoExpire()
                    .autoBump()
                    .removeMilestone(),
            BugAction.create(Action.PRIORITY_LOW)
                    .priority(Priority.LOW)
                    .comment()
                    .helpWanted()
                    .autoExpire()
                    .autoBump()
                    .removeMilestone()
    );

    @Inject
    GitHubInstallationProvider gitHubProvider;

    @Inject
    BugActionMessages messages;

    public void runAction(Action action, GHIssue issue) throws IOException {
        Optional<BugAction> first = actions.values().stream().filter(a -> a.action.equals(action)).findFirst();
        if (first.isPresent()) {
            runAction(first.get(), issue);
        } else {
            throw new IllegalArgumentException("Unknown action " + action);
        }
    }

    private void runAction(BugAction action, GHIssue issue) throws IOException {
        if (action != null) {
            logger.infov("Running action={0} on issue={1}", action.action.toLabel(), issue.getHtmlUrl());

            Set<String> labels = issue.getLabels().stream().map(GHLabel::getName).collect(Collectors.toSet());

            if (labels.contains(Labels.KIND_BUG)) {
                List<String> labelsToAdd = new LinkedList<>();
                List<String> labelsToRemove = new LinkedList<>();

                if (action.priority != null) {
                    labels.stream().filter(Priority::isInstance).forEach(labelsToRemove::add);

                    labelsToAdd.add(action.priority.toLabel());
                }

                labels.stream().filter(Status::isInstance).forEach(labelsToRemove::add);

                if (action.status != null) {
                    labelsToAdd.add(action.status.toLabel());
                }

                if (action.kind != null) {
                    labelsToAdd.add(action.kind.toLabel());
                }

                if (action.helpWanted) {
                    labelsToAdd.add(Label.HELP_WANTED.toLabel());
                }

                if (action.autoExpire) {
                    labelsToAdd.add(Status.AUTO_EXPIRE.toLabel());
                }

                if (action.autoBump) {
                    labelsToAdd.add(Status.AUTO_BUMP.toLabel());
                }

                labelsToRemove.add(action.action.toLabel());

                labelsToRemove.removeAll(labelsToAdd);
                issue.removeLabels(labelsToRemove.toArray(new String[0]));

                if (!labelsToAdd.isEmpty()) {
                    issue.addLabels(labelsToAdd.toArray(new String[0]));
                }

                if (action.closeAsNotPlanned) {
                    issue.close(GHIssueStateReason.NOT_PLANNED);
                } else {
                    if (issue.getState().equals(GHIssueState.CLOSED)) {
                        issue.reopen();
                    }

                    if (action.setNextMileStone) {
                        GHMilestone nextMajorRelease = getNextMajorRelease(issue.getRepository());
                        issue.setMilestone(nextMajorRelease);
                    } else if (action.removeMileStone) {
                        issue.setMilestone(null);
                    }
                }

                // Comment should be the last thing the bot does!
                if (action.comment) {
                    for (GHIssueComment c : issue.getComments()) {
                        if (c.getUser().getLogin().equals(gitHubProvider.getBotLogin())) {
                            c.delete();
                        }
                    }

                    issue.comment(messages.getBugActionComment(action.action));
                }
            }
        }
    }

    static Map<String, BugAction> initActions(BugAction... bugActions) {
        Map<String, BugAction> map = new HashMap<>();
        for (BugAction bugAction : bugActions) {
            map.put(bugAction.action.toLabel(), bugAction);
        }
        return map;
    }

    private GHMilestone getNextMajorRelease(GHRepository repository) {
        PagedIterator<GHMilestone> itr = repository.listMilestones(GHIssueState.OPEN).iterator();
        GHMilestone nextMajorMilestone = null;
        Integer nextMajorRelease = null;
        while (itr.hasNext()) {
            GHMilestone next = itr.next();
            Matcher matcher = MAJOR_VERSION_PATTERN.matcher(next.getTitle());
            if (matcher.matches()) {
                Integer majorVersion = Integer.valueOf(matcher.group(1));
                if (nextMajorRelease == null || majorVersion < nextMajorRelease) {
                    nextMajorRelease = majorVersion;
                    nextMajorMilestone = next;
                }
            }
        }
        return nextMajorMilestone;
    }

    static Properties initProperties() {
        Properties properties = new Properties();
        try {
            properties.load(BugActions.class.getResourceAsStream("bug-action-messages.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    static final class BugAction {

        Action action;
        boolean comment = false;

        Priority priority;
        Status status;
        Kind kind;
        boolean closeAsNotPlanned = false;
        boolean setNextMileStone = false;
        boolean removeMileStone = false;
        boolean helpWanted = false;
        boolean autoExpire = false;
        boolean autoBump = false;

        public static BugAction create(Action action) {
            return new BugAction(action);
        }

        public BugAction(Action action) {
            this.action = action;
        }

        BugAction comment() {
            comment = true;
            return this;
        }

        BugAction closeAsNotPlanned() {
            closeAsNotPlanned = true;
            return this;
        }

        BugAction priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        BugAction status(Status status) {
            this.status = status;
            return this;
        }

        BugAction kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        BugAction helpWanted() {
            this.helpWanted = true;
            return this;
        }

        BugAction nextMilestone() {
            this.setNextMileStone = true;
            return this;
        }

        BugAction removeMilestone() {
            this.removeMileStone = true;
            return this;
        }

        BugAction autoExpire() {
            this.autoExpire = true;
            return this;
        }

        BugAction autoBump() {
            this.autoBump = true;
            return this;
        }

    }

}