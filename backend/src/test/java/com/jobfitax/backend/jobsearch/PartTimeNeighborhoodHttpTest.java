package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
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
@Import(PartTimeNeighborhoodHttpTest.Fixtures.class)
class PartTimeNeighborhoodHttpTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary
        PartTimeSearchService fixturePartTimeService(JobSearchRegionService regions) throws Exception {
            String mon;
            try (var input = getClass().getResourceAsStream("/part-time/mon-area.html")) {
                mon = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\"totalCount\": 142", "\"totalCount\": 2");
            }
            String alba = "<input name='strAreaMulti' value='02||강남구||개포동'><div>총 2건<div id='SearchJob'><ul id='jobNormal'>"
                    + "<li data-imid='123'><span class='job-list__subject'>모집</span><span class='job-list__area'>서울 강남구 개포동</span></li>"
                    + "<li data-imid='456'><span class='job-list__subject'>모집</span><span class='job-list__area'>서울 강남구</span></li>"
                    + "</ul></div></div>";
            return new PartTimeSearchService(regions, new PartTimeNeighborhoods(code ->
                    "[{\"code\":\"I0100050\",\"name\":\"개포동\"}]"),
                    url -> url.contains("www.albamon.com") ? mon : alba);
        }
    }
    private HttpResponse<String> post(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/part-time-search/pages"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    @Test void fullCollectionStreamsProgressAndOnlyThenTheCompleteResult() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/part-time-search/collect"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("""
                    {"criteria":{"regionIds":["I000_I010"],"neighborhoodId":"I0100050"}}
                    """)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/x-ndjson"));
        var mapper = new ObjectMapper();
        var updates = response.body().lines().map(mapper::readTree).toList();
        assertEquals(java.util.List.of("checking", "collecting", "sorting", "complete"),
                updates.stream().map(node -> node.path("phase").asText()).toList(), response.body());
        assertEquals(4, updates.getLast().path("result").path("items").size());
    }

    @Test void locationArrayIsAcceptedAndDuplicateAreaIsNotCountedTwice() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/part-time-search/collect"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("""
                    {"criteria":{"locations":[
                      {"regionId":"I000_I010","neighborhoodId":"I0100050","neighborhoodName":"개포동"},
                      {"regionId":"I000_I010","neighborhoodId":"I0100050","neighborhoodName":"개포동"}]}}
                    """)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),response.body());
        var mapper = new ObjectMapper();
        var last = response.body().lines().map(mapper::readTree).toList().getLast();
        assertEquals("complete",last.path("phase").asText(),response.body());
        assertEquals(4,last.path("total").asInt());
        assertEquals(400,post("""
            {"criteria":{"locations":[{"regionId":"I000_I010","neighborhoodId":"I0100050"}]},"albamonPage":1,"albaheavenPage":1}
            """).statusCode(), "The legacy page API must never silently ignore selected locations");
    }
    @Test void regionOnlyAndNeighborhoodSearchWorkThroughHttpWithoutKeyword() throws Exception {
        var response = post("""
            {"criteria":{"regionIds":["I000_I010"],"neighborhoodId":"I0100050"},"albamonPage":1,"albaheavenPage":1}
            """);
        assertEquals(200, response.statusCode(), response.body());
        var json = new ObjectMapper().readTree(response.body());
        for (var site : json.path("sites")) assertEquals("SUCCESS", site.path("status").asText(), response.body());
        assertTrue(json.path("items").size() > 1);
        assertTrue(json.path("items").valueStream().anyMatch(item -> item.path("postingId").asText().equals("456")),
                "Native source results must not be dropped because their displayed address omits the dong");
        var district = post("""
            {"criteria":{"keyword":"  ","regionIds":["I000_I010"]},"albamonPage":1,"albaheavenPage":null}
            """);
        assertEquals(200, district.statusCode(), district.body());
    }
    @Test void neitherConditionAndForeignNeighborhoodAreRejectedThroughHttp() throws Exception {
        assertEquals(400, post("""
            {"criteria":{"keyword":"  ","regionIds":[]},"albamonPage":1,"albaheavenPage":1}
            """).statusCode());
        assertEquals(400, post("""
            {"criteria":{"keyword":"","regionIds":["I000_I010"],"neighborhoodId":"I0200010"},"albamonPage":1,"albaheavenPage":1}
            """).statusCode());
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/part-time-search/neighborhoods?regionId=I000_I010")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("개포동"));
    }
}
