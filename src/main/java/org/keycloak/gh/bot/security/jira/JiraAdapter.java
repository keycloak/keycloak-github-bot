package org.keycloak.gh.bot.security.jira;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class JiraAdapter {

    private static final Logger LOG = Logger.getLogger(JiraAdapter.class);

    private final String jiraUrl;
    private final String jiraToken;
    private final Duration timeout;
    private final HttpClient client;

    @Inject
    public JiraAdapter(
            @ConfigProperty(name = "jira.url") Optional<String> jiraUrl,
            @ConfigProperty(name = "jira.pat") Optional<String> jiraToken,
            @ConfigProperty(name = "bot.jira.timeout", defaultValue = "10s") Duration timeout) {
        this(jiraUrl.orElse(null), jiraToken.orElse(null), timeout,
                HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    protected JiraAdapter(String jiraUrl, String jiraToken, Duration timeout, HttpClient client) {
        this.jiraUrl = jiraUrl;
        this.jiraToken = jiraToken;
        this.timeout = timeout;
        this.client = client;
    }

    public record JiraIssue(String key, String summary, String description) {}

    public Optional<JiraIssue> findIssueByCve(String cveId) {
        if (jiraUrl == null || jiraToken == null) {
            LOG.warn("Jira integration is disabled. Missing 'jira.url' or 'jira.pat' configuration.");
            return Optional.empty();
        }

        try {
            String jql = String.format(
                    "project IN (RHBK, RHSSO) AND status IN (Open, Reopened, New, Review) AND (summary ~ \"%s\" OR description ~ \"%s\")",
                    cveId, cveId
            );

            String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
            URI uri = URI.create(jiraUrl + "/rest/api/2/search?maxResults=1&fields=summary,description&jql=" + encodedJql);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Bearer " + jiraToken)
                    .header("Accept", "application/json")
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.errorf("Jira API error. Status: %d, Body: %s", response.statusCode(), response.body());
                return Optional.empty();
            }

            return parseResponse(response.body());

        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to fetch issue from Jira", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<JiraIssue> parseResponse(String jsonBody) {
        try {
            JsonObject root = JsonParser.parseString(jsonBody).getAsJsonObject();
            JsonArray issues = root.getAsJsonArray("issues");

            if (issues == null || issues.isEmpty()) {
                return Optional.empty();
            }

            JsonObject issue = issues.get(0).getAsJsonObject();
            String key = issue.get("key").getAsString();

            JsonObject fields = issue.getAsJsonObject("fields");
            String summary = getSafeString(fields, "summary");
            String description = getSafeString(fields, "description");

            return Optional.of(new JiraIssue(key, summary, description));

        } catch (Exception e) {
            LOG.error("Failed to parse Jira JSON response", e);
            return Optional.empty();
        }
    }

    private String getSafeString(JsonObject obj, String memberName) {
        JsonElement element = obj.get(memberName);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : "";
    }
}