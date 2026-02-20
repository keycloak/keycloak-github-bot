package org.keycloak.gh.bot.security;

import com.github.rvesse.airline.annotations.Cli;
import com.github.rvesse.airline.annotations.Command;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHIssue;

import java.io.IOException;

/**
 * Defines the command-line interface and command handlers for the anxiety42 bot.
 */
@Cli(name = "@anxiety42-bot", defaultCommand = SecurityBotCli.LogCommand.class, commands = { SecurityBotCli.LogCommand.class })
public class SecurityBotCli {

    /**
     * Interface contract required by Quarkus GitHub App to pass execution context dynamically.
     */
    public interface BotCommand {
        void run(GHEventPayload.IssueComment issueCommentPayload) throws IOException;
    }

    /**
     * Executes the logging operation using the dynamically injected method parameters.
     */
    @Command(name = "log", description = "Logs the event payload details upon mention")
    public static class LogCommand implements BotCommand {

        private static final Logger LOG = Logger.getLogger(LogCommand.class);

        @Override
        public void run(GHEventPayload.IssueComment issueCommentPayload) {
            try {
                executeSafely(issueCommentPayload);
            } catch (Exception e) {
                LOG.error("Failed to process Airline command execution.", e);
                replyWithError(issueCommentPayload, e);
            }
        }

        private void executeSafely(GHEventPayload.IssueComment payload) throws IOException {
            String repositoryName = payload.getRepository().getFullName();
            String userLogin = payload.getComment().getUser().getLogin();
            String commentUrl = payload.getComment().getHtmlUrl().toString();

            LOG.infof("Bot invoked in repository [%s] by user [%s]. Context: %s",
                    repositoryName, userLogin, commentUrl);

            payload.getIssue().comment("✅ **Bot Execution Successful**");
        }

        private void replyWithError(GHEventPayload.IssueComment payload, Exception exception) {
            try {
                GHIssue issue = payload.getIssue();
                String errorMessage = String.format(
                        "⚠️ **Bot Execution Failure**\nI encountered an error while processing your command.\n\n`%s: %s`",
                        exception.getClass().getSimpleName(),
                        exception.getMessage()
                );
                issue.comment(errorMessage);
            } catch (IOException ioException) {
                LOG.error("Critical failure: Could not send error message to GitHub API.", ioException);
            }
        }
    }
}