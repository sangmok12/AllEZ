package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"SUPABASE_URL=", "SUPABASE_SECRET_KEY="})
@Import(JobCollectionHttpTest.Fixtures.class)
class JobCollectionHttpTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @TestConfiguration
    static class Fixtures {
        @Bean @Primary JobSearchService fixtureJobSearch() {
            var service = mock(JobSearchService.class);
            when(service.searchPageForCollection(any())).thenAnswer(call -> {
                JobSearchPageRequest request = call.getArgument(0);
                if (request.criteria().keyword().equals("wide")) return new JobSearchResponse(List.of(), 0,
                        List.of(site("JOBKOREA", 1001, 1), site("SARAMIN", 1000, 1)));
                if (request.criteria().keyword().equals("fail")) return new JobSearchResponse(List.of(), 0,
                        List.of(site("JOBKOREA", 0, null), new JobSearchResponse.SiteResult("SARAMIN", "사람인", "FAILED", 0, 0, 0, "실패")));
                if (request.jobKoreaPage() == 0) return new JobSearchResponse(
                        List.of(item("JOBKOREA", "old", "2026-11-07T00:00:00+09:00"), item("SARAMIN", "middle", "2026-11-08T00:00:00+09:00")),
                        2, List.of(site("JOBKOREA", 2, 1), site("SARAMIN", 1, null)));
                return new JobSearchResponse(List.of(item("JOBKOREA", "new", "2026-11-09T00:00:00+09:00")), 1,
                        List.of(site("JOBKOREA", 2, null)));
            });
            return service;
        }
        private static JobSearchResponse.SiteResult site(String source, int total, Integer next) {
            return new JobSearchResponse.SiteResult(source, source, "SUCCESS", 0, total, next, "");
        }
        private static JobSearchItem item(String source, String id, String date) {
            return new JobSearchItem(source, source, id, "회사", "공고", List.of(), "", "", List.of(), List.of(),
                    date.substring(0, 10), "", "https://example.com/" + id, OffsetDateTime.parse(date));
        }
    }

    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/job-search/collect"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void streamsProgressThenOneGloballySortedCompleteResult() throws Exception {
        var response = post("{\"criteria\":{\"keyword\":\"java\"}}");
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/x-ndjson"));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-transform"));
        var updates = response.body().lines().map(new ObjectMapper()::readTree).toList();
        assertEquals(List.of("checking", "collecting", "collecting", "sorting", "complete"), updates.stream().map(n -> n.path("phase").asText()).toList());
        assertTrue(updates.subList(0, 4).stream().noneMatch(n -> n.path("result").isObject()));
        var last = updates.getLast();
        assertEquals(3, last.path("total").asInt());
        assertEquals("new", last.path("result").path("items").get(0).path("postingId").asText());
    }

    @Test void rejectsOversizedSearchAndDoesNotPublishFailedSourceResults() throws Exception {
        var mapper = new ObjectMapper();
        for (String keyword : List.of("wide", "fail")) {
            var response = post("{\"criteria\":{\"keyword\":\"" + keyword + "\"}}");
            var updates = response.body().lines().map(mapper::readTree).toList();
            assertEquals(keyword.equals("wide") ? "rejected" : "error", updates.getLast().path("phase").asText());
            assertTrue(updates.stream().noneMatch(n -> n.path("phase").asText().equals("complete")));
        }
    }

    @Test void invalidCriteriaAreRejectedBeforeStartingAStream() throws Exception {
        assertEquals(400, post("{\"criteria\":{\"keyword\":\"\"}}").statusCode());
        assertEquals(400, post("{}").statusCode());
        assertEquals(400, post("null").statusCode());
    }
}
