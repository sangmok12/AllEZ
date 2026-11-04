package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchApplicationTest {
    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void servesRegionsWithoutAiCredentials() throws Exception {
        var response = client.send(HttpRequest.newBuilder(endpoint("/api/job-search/regions")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("서울특별시", "강남구");
    }

    @Test
    void rejectsEmptySearch() throws Exception {
        var request = HttpRequest.newBuilder(endpoint("/api/job-search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"keyword\":\"\"}")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void doesNotExposeAnalysisEndpoint() throws Exception {
        var response = client.send(HttpRequest.newBuilder(endpoint("/api/analysis")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidPageWithoutCallingUpstream() throws Exception {
        var request = HttpRequest.newBuilder(endpoint("/api/job-search/pages"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"criteria\":{\"keyword\":\"Java\"},\"jobKoreaPage\":-1,\"saraminPage\":0}")).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
