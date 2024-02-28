package org.keycloak.gh.bot;

import io.quarkus.runtime.Startup;
import jakarta.inject.Singleton;
import org.keycloak.gh.bot.labels.Action;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Singleton
@Startup
public class BugActionMessages {

    Properties properties;

    public BugActionMessages() {
        properties = new Properties();
        try {
            properties.load(BugActions.class.getResourceAsStream("bug-action-messages.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getBugActionComment(Action action) {
        return properties.getProperty("action-" + action.name());
    }

    public String getExpireComment(long value, TimeUnit timeUnit) {
        return MessageFormat.format(properties.getProperty("expired"), value, timeUnit.name().toLowerCase(Locale.ENGLISH));
    }

}
