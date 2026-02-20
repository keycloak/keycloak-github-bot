package org.keycloak.gh.bot.utils;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Handles thread throttling to enforce rate limits.
 * Encapsulating this allows for zero-delay mocking during tests.
 */
@ApplicationScoped
public class Throttler {

    private static final Logger LOG = Logger.getLogger(Throttler.class);

    public void throttle(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            LOG.warn("Throttling interrupted, restoring interrupt status");
            Thread.currentThread().interrupt();
        }
    }
}