package org.keycloak.gh.bot.security.jira;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JiraAdapterTest {

    private JiraAdapter adapter;
    private HttpClient mockClient;

    @BeforeEach
    public void setup() {
        mockClient = mock(HttpClient.class);
        adapter = new JiraAdapter("https://issues.redhat.com", "token123", Duration.ofSeconds(5), mockClient);
    }

    @Test
    public void testFindIssueByCve_Success() throws IOException, InterruptedException {
        String cve = "CVE-2023-1234";
        String jsonResponse = """
                {
                  "issues": [
                    {
                      "key": "RHBK-100",
                      "fields": {
                        "summary": "CVE-2023-1234 Fix bug",
                        "description": "Flaw:\\nBug detail"
                      }
                    }
                  ]
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        when(mockClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse);

        Optional<JiraAdapter.JiraIssue> result = adapter.findIssueByCve(cve);

        assertTrue(result.isPresent());
        assertEquals("RHBK-100", result.get().key());
        assertEquals("CVE-2023-1234 Fix bug", result.get().summary());
    }

    @Test
    public void testFindIssueByCve_CorrectUrlConstruction() throws IOException, InterruptedException {
        String cve = "CVE-2023-1234";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("{}");

        when(mockClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(mockResponse);

        adapter.findIssueByCve(cve);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockClient).send(captor.capture(), any());

        String uri = captor.getValue().uri().toString();
        assertTrue(uri.startsWith("https://issues.redhat.com/rest/api/2/search"));
        assertTrue(uri.contains("jql="));
        assertTrue(uri.contains("project+IN+%28RHBK%2C+RHSSO%29"));
    }
}