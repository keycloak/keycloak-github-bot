package org.keycloak.gh.bot.email;

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
import java.util.Properties;

/**
 * Handles the construction and sending of MIME emails via the Gmail API.
 */
@ApplicationScoped
public class MailSender {

    private static final Logger LOG = Logger.getLogger(MailSender.class);

    @ConfigProperty(name = "gmail.user.email") String botEmail;

    @Inject GmailAdapter gmail;

    private Session mailSession;

    @PostConstruct
    public void init() {
        this.mailSession = Session.getDefaultInstance(new Properties(), null);
    }

    public boolean sendNewEmail(String to, String cc, String subject, String body) {
        try {
            MimeMessage email = createBaseMessage();

            email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));

            if (cc != null && !cc.isBlank()) {
                email.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(cc));
            }

            email.setSubject(subject);
            email.setText(body);

            gmail.sendMessage(null, email);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to send new email", e);
            return false;
        }
    }

    public boolean sendReply(String threadId, String subject, String body, String ccTarget) {
        try {
            com.google.api.services.gmail.model.Thread thread = gmail.getThread(threadId);
            if (thread == null || thread.getMessages() == null) return false;

            Message targetMsg = findLastHumanMessage(thread.getMessages());
            if (targetMsg == null) return false;

            MimeMessage email = createBaseMessage();
            setupThreadingHeaders(email, targetMsg);

            String sender = gmail.getHeader(targetMsg, "From");
            if (sender != null) email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(sender));
            if (ccTarget != null) email.addRecipient(jakarta.mail.Message.RecipientType.CC, new InternetAddress(ccTarget));

            email.setSubject(subject.startsWith("Re:") ? subject : "Re: " + subject);
            email.setText(body);

            gmail.sendMessage(threadId, email);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to send reply email", e);
            return false;
        }
    }

    private MimeMessage createBaseMessage() throws Exception {
        MimeMessage email = new MimeMessage(mailSession);
        email.setFrom(new InternetAddress(botEmail));
        return email;
    }

    private void setupThreadingHeaders(MimeMessage email, Message targetMsg) throws Exception {
        String parentId = gmail.getHeader(targetMsg, "Message-ID");
        String refs = gmail.getHeader(targetMsg, "References");
        if (parentId != null && !parentId.isEmpty()) {
            email.setHeader("In-Reply-To", parentId);
            email.setHeader("References", (refs == null || refs.isEmpty() ? "" : refs + " ") + parentId);
        }
    }

    private Message findLastHumanMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            String from = gmail.getHeader(history.get(i), "From");
            if (from != null && !from.toLowerCase().contains(botEmail.toLowerCase())) return history.get(i);
        }
        return history.get(history.size() - 1);
    }
}