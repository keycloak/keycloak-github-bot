package org.keycloak.gh.bot.security.email;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    @ConfigProperty(name = "bot.security.enabled", defaultValue = "true")
    boolean securityEnabled;

    @Scheduled(every = "${bot.email.sync.interval:10s}", concurrentExecution = ConcurrentExecution.SKIP)
    public void syncGmailToGitHub() {
        if (securityEnabled) {
            LOG.trace("Syncing Security Emails...");
            mailProcessor.processUnreadEmails();
        }
    }

    @Scheduled(every = "${bot.command.process.interval:10s}", concurrentExecution = ConcurrentExecution.SKIP)
    public void processGitHubCommands() {
        if (securityEnabled) {
            LOG.trace("Processing Security Commands...");
            commandProcessor.processCommands();
        }
    }

    @Scheduled(every = "${bot.jira.sync.interval:1h}", concurrentExecution = ConcurrentExecution.SKIP)
    public void syncJiraToGitHub() {
        if (securityEnabled) {
            LOG.info("Syncing Jira Updates...");
            jiraProcessor.processJiraUpdates();
        }
    }

    void setSecurityEnabled(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }
}