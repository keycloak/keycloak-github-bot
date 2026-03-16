package org.keycloak.gh.bot.security.email;

import com.google.api.services.gmail.model.Message;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Sends new emails and threaded replies via the Gmail API, preserving conversation threading headers.
 */
@ApplicationScoped
public class MailSender {

    private static final Logger LOG = Logger.getLogger(MailSender.class);

    @ConfigProperty(name = "gmail.user.email")
    String botEmail;

    @Inject
    GmailAdapter gmail;

    private Session mailSession;

    @PostConstruct
    public void init() {
        this.mailSession = Session.getDefaultInstance(new Properties(), null);
    }

    public Optional<String> sendNewEmail(String to, String cc, String subject, String body) {
        try {
            MimeMessage email = createBaseMessage();
            addRecipients(email, to, cc);
            email.setSubject(subject);
            email.setText(body);

            Message sentMessage = gmail.sendMessage(null, email);
            String threadId = sentMessage != null ? sentMessage.getThreadId() : null;
            if (threadId == null) {
                LOG.errorf("Gmail API returned no thread ID after sending to %s. Email may have been delivered but thread tracking is broken.", to);
                return Optional.empty();
            }
            LOG.infof("New email sent to %s (CC: %s), threadId=%s", to, cc, threadId);
            return Optional.of(threadId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send new email to %s", to);
            return Optional.empty();
        }
    }

    public boolean sendThreadedEmail(String threadId, String to, String cc, String body) {
        try {
            com.google.api.services.gmail.model.Thread thread = fetchThread(threadId);
            if (thread == null) return false;

            MimeMessage email = buildThreadedMessage(thread, to, cc, body);
            gmail.sendMessage(threadId, email);
            LOG.infof("Threaded email sent to thread %s (TO: %s, CC: %s)", threadId, to, cc);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send threaded email to thread %s", threadId);
            return false;
        }
    }

    public boolean sendReply(String threadId, String body, String ccTarget) {
        try {
            com.google.api.services.gmail.model.Thread thread = fetchThread(threadId);
            if (thread == null) return false;

            Message lastHumanMsg = findLastHumanMessage(thread.getMessages());
            if (lastHumanMsg == null) return false;

            Map<String, String> recipientHeaders = gmail.getHeadersMap(lastHumanMsg);
            String sender = recipientHeaders.getOrDefault("Reply-To", recipientHeaders.get("From"));

            MimeMessage email = buildThreadedMessage(thread, sender, ccTarget, body);
            gmail.sendMessage(threadId, email);
            LOG.infof("Reply sent to thread %s (TO: %s, CC: %s)", threadId, sender, ccTarget);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send reply to thread %s", threadId);
            return false;
        }
    }

    private com.google.api.services.gmail.model.Thread fetchThread(String threadId) throws Exception {
        com.google.api.services.gmail.model.Thread thread = gmail.getThread(threadId);
        if (thread == null || thread.getMessages() == null || thread.getMessages().isEmpty()) return null;
        return thread;
    }

    private MimeMessage buildThreadedMessage(com.google.api.services.gmail.model.Thread thread, String to, String cc, String body) throws Exception {
        Message lastMsg = thread.getMessages().get(thread.getMessages().size() - 1);
        Map<String, String> headers = gmail.getHeadersMap(lastMsg);

        MimeMessage email = createBaseMessage();
        addRecipients(email, to, cc);
        setSubjectFromOriginal(email, headers);
        setupThreadingHeaders(email, headers);
        email.setText(body);
        return email;
    }

    private MimeMessage createBaseMessage() throws Exception {
        MimeMessage email = new MimeMessage(mailSession);
        email.setFrom(new InternetAddress(botEmail));
        return email;
    }

    private void addRecipients(MimeMessage email, String to, String cc) throws Exception {
        if (to != null) email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        if (cc != null && !cc.isBlank()) email.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
    }

    private void setSubjectFromOriginal(MimeMessage email, Map<String, String> headers) throws Exception {
        String originalSubject = headers.getOrDefault("Subject", "No Subject");
        String newSubject = originalSubject.toLowerCase().startsWith("re:")
                ? originalSubject
                : "Re: " + originalSubject;
        email.setSubject(newSubject);
    }

    private void setupThreadingHeaders(MimeMessage email, Map<String, String> headers) throws Exception {
        String parentId = headers.get("Message-ID");
        String refs = headers.get("References");
        if (parentId != null && !parentId.isEmpty()) {
            email.setHeader("In-Reply-To", parentId);
            email.setHeader("References", (refs == null || refs.isEmpty() ? "" : refs + " ") + parentId);
        }
    }

    Message findLastHumanMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!isBotMessage(history.get(i))) {
                return history.get(i);
            }
        }
        return history.get(history.size() - 1);
    }

    private boolean isBotMessage(Message msg) {
        String from = gmail.getHeadersMap(msg).get("From");
        return from == null || from.toLowerCase().contains(botEmail.toLowerCase());
    }
}
