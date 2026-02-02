package org.keycloak.gh.bot.security.email;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.keycloak.gh.bot.security.jira.JiraProcessor;

/**
 * Manages the scheduled execution of security email synchronization and command tasks.
 */
@ApplicationScoped
public class EmailSyncScheduler {

    private static final Logger LOG = Logger.getLogger(EmailSyncScheduler.class);

    @Inject MailProcessor mailProcessor;
    @Inject CommandProcessor commandProcessor;
    @Inject JiraProcessor jiraProcessor;

    @Scheduled(every = "${bot.email.sync.interval:10s}", concurrentExecution = ConcurrentExecution.SKIP)
    public void syncGmailToGitHub() {
        LOG.trace("Syncing Security Emails...");
        mailProcessor.processUnreadEmails();
    }

    @Scheduled(every = "${bot.command.process.interval:10s}", concurrentExecution = ConcurrentExecution.SKIP)
    public void processGitHubCommands() {
        LOG.trace("Processing Security Commands...");
        commandProcessor.processCommands();
    }

    @Scheduled(every = "${bot.jira.sync.interval:1h}", concurrentExecution = ConcurrentExecution.SKIP)
    public void syncJiraToGitHub() {
        LOG.info("Syncing Jira Updates...");
        jiraProcessor.processJiraUpdates();
    }
}