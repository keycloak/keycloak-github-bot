package org.keycloak.gh.bot.security.email;

import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads externalized email message templates for the security pipeline.
 */
@Singleton
@Startup
public class SecurityEmailMessages {

    private static final String RESOURCE = "security-email-messages.properties";
    private static final String ACK_KEY = "ack.mailing-list";

    private final String acknowledgmentMessage;

    public SecurityEmailMessages() {
        var properties = new Properties();
        try (InputStream is = SecurityEmailMessages.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Missing classpath resource: %s".formatted(RESOURCE));
            }
            properties.load(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        acknowledgmentMessage = properties.getProperty(ACK_KEY);
        if (acknowledgmentMessage == null) {
            throw new IllegalStateException("Missing property '%s' in %s".formatted(ACK_KEY, RESOURCE));
        }
    }

    public String getAcknowledgmentMessage() {
        return acknowledgmentMessage;
    }

}
