package org.keycloak.gh.bot.security.email;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.security.GeneralSecurityException;

@ApplicationScoped
public class GmailProducer {

    @ConfigProperty(name = "gmail.client.id")
    String clientId;

    @ConfigProperty(name = "gmail.client.secret")
    String clientSecret;

    @ConfigProperty(name = "gmail.refresh.token")
    String refreshToken;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "keycloak-github-bot")
    String appName;

    @Produces
    @Singleton
    public Gmail createGmailClient() {
        try {
            var credentials = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();

            return new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(appName)
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize Gmail Client", e);
        }
    }
}