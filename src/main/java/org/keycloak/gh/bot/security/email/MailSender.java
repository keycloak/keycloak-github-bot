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
import java.util.Properties;

/**
 * Sends threaded replies via the Gmail API, preserving conversation threading headers.
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

    public boolean sendReply(String threadId, String body, String ccTarget) {
        try {
            com.google.api.services.gmail.model.Thread thread = gmail.getThread(threadId);
            if (thread == null || thread.getMessages() == null || thread.getMessages().isEmpty()) return false;

            Message lastMsg = thread.getMessages().get(thread.getMessages().size() - 1);
            Map<String, String> threadingHeaders = gmail.getHeadersMap(lastMsg);

            Message lastHumanMsg = findLastHumanMessage(thread.getMessages());
            if (lastHumanMsg == null) return false;

            Map<String, String> recipientHeaders = gmail.getHeadersMap(lastHumanMsg);
            String sender = recipientHeaders.getOrDefault("Reply-To", recipientHeaders.get("From"));

            MimeMessage email = createBaseMessage();
            setupThreadingHeaders(email, threadingHeaders);

            if (sender != null) email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(sender));
            if (ccTarget != null) email.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(ccTarget));

            setSubjectFromOriginal(email, threadingHeaders);
            email.setText(body);

            gmail.sendMessage(threadId, email);
            LOG.infof("Reply sent to thread %s (TO: %s, CC: %s)", threadId, sender, ccTarget);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to send reply to thread %s", threadId);
            return false;
        }
    }

    private MimeMessage createBaseMessage() throws Exception {
        MimeMessage email = new MimeMessage(mailSession);
        email.setFrom(new InternetAddress(botEmail));
        return email;
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
            Message msg = history.get(i);
            Map<String, String> headers = gmail.getHeadersMap(msg);
            String from = headers.get("From");

            if (from != null && !from.toLowerCase().contains(botEmail.toLowerCase())) {
                return msg;
            }
        }
        return history.get(history.size() - 1);
    }
}
