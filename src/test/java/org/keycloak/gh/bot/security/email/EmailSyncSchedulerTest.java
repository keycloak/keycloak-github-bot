package org.keycloak.gh.bot.security.email;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.gh.bot.security.jira.JiraProcessor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
public class EmailSyncSchedulerTest {

    @Inject
    EmailSyncScheduler scheduler;

    @InjectMock
    MailProcessor mailProcessor;

    @InjectMock
    CommandProcessor commandProcessor;

    @InjectMock
    JiraProcessor jiraProcessor;

    @AfterEach
    public void tearDown() {
        scheduler.setSecurityEnabled(true);
    }

    @Test
    public void testSyncsAreEnabledByDefault() {
        scheduler.setSecurityEnabled(true);

        scheduler.syncGmailToGitHub();
        scheduler.processGitHubCommands();
        scheduler.syncJiraToGitHub();

        verify(mailProcessor, times(1)).processUnreadEmails();
        verify(commandProcessor, times(1)).processCommands();
        verify(jiraProcessor, times(1)).processJiraUpdates();
    }

    @Test
    public void testSyncsAreDisabled() {
        scheduler.setSecurityEnabled(false);

        scheduler.syncGmailToGitHub();
        scheduler.processGitHubCommands();
        scheduler.syncJiraToGitHub();

        verify(mailProcessor, never()).processUnreadEmails();
        verify(commandProcessor, never()).processCommands();
        verify(jiraProcessor, never()).processJiraUpdates();
    }
}