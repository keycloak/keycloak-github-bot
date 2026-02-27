package org.keycloak.gh.bot.security.email;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EmailSyncScheduler {

    private static final Logger LOGGER = Logger.getLogger(EmailSyncScheduler.class);
    @Inject
    MailProcessor mailProcessor;

    @ConfigProperty(name = "bot.security.enabled", defaultValue = "true")
    boolean securityEnabled;

    @Scheduled(every = "${bot.email.sync.interval:5m}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void syncGmailToGitHub() {
        if (!securityEnabled) {
            return;
        }
        mailProcessor.processUnreadEmails();
    }
}