package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class PartTimeNeighborhoodsTest {
    private static final String CODES = """
        [{"code":"I0100050","name":"개포동"},{"code":"I0100010","name":"개포1동"}]
        """;
    private PartTimeSearchService service() {
        return new PartTimeSearchService(new JobSearchRegionService(new ObjectMapper()),
                new PartTimeNeighborhoods(code -> CODES));
    }
    private JobSearchRequest criteria(String keyword, List<String> regions, String neighborhood) {
        return new JobSearchRequest(keyword, regions, "ANY", null, null, "ANY", List.of(), neighborhood);
    }

    @Test void regionOnlyIsValidButNeitherConditionIsRejected() {
        for (String keyword : new String[] {null, "", "  "}) {
            assertDoesNotThrow(() -> PartTimeSearchService.validateCriteria(criteria(keyword, List.of("I000_I010"), null)));
            assertThrows(ResponseStatusException.class,
                    () -> PartTimeSearchService.validateCriteria(criteria(keyword, List.of(), null)));
        }
        assertDoesNotThrow(() -> PartTimeSearchService.validateCriteria(criteria("카페", List.of(), null)));
        assertThrows(ResponseStatusException.class,
                () -> PartTimeSearchService.validateCriteria(criteria("가".repeat(21), List.of("I000"), null)));
    }

    @Test void nativeNeighborhoodsAreCachedAndFailureIsRetryable() {
        var calls = new AtomicInteger();
        var neighborhoods = new PartTimeNeighborhoods(code -> { calls.incrementAndGet(); return CODES; });
        assertEquals("개포동", neighborhoods.list("I010").getFirst().name());
        assertEquals(2, neighborhoods.list("I010").size());
        assertEquals(1, calls.get());
        var attempts = new AtomicInteger();
        var retry = new PartTimeNeighborhoods(code -> attempts.incrementAndGet() == 1 ? "<html>Blocked</html>" : CODES);
        assertThrows(ResponseStatusException.class, () -> retry.list("I010"));
        assertEquals(2, retry.list("I010").size());
        assertThrows(ResponseStatusException.class, () -> neighborhoods.list("../I010"));
        assertThrows(ResponseStatusException.class, () -> new PartTimeNeighborhoods(code -> CODES).list("B201"));
    }

    @Test void rejectsNeighborhoodFromAnotherDistrictBeforeSearching() {
        var service = service();
        assertThrows(ResponseStatusException.class, () -> service.neighborhoods("I000"));
        assertThrows(ResponseStatusException.class, () -> service.search(new PartTimeSearchService.Request(
                criteria("", List.of("I000_I010"), "I0200010"), 1, 1)));
        assertThrows(ResponseStatusException.class, () -> service.search(new PartTimeSearchService.Request(
                criteria("카페", List.of(), "I0100050"), 1, 1)));
    }

    @Test void usesAreaEndpointForEmptyKeywordAndKeepsKeywordAndNeighborhoodSeparate() {
        var region = new PartTimeSearchService.Region("I010", "02", "강남구");
        var neighborhood = new PartTimeNeighborhoods.Neighborhood("I0100050", "개포동");
        String area = PartTimeSearchService.sourceUrl("ALBAMON", "", region, neighborhood, 2);
        assertEquals("https://www.albamon.com/jobs/area?page=2&areas=I0100050", area);
        String search = PartTimeSearchService.sourceUrl("ALBAMON", "카페", region, neighborhood, 1);
        assertTrue(search.contains("/total-search?keyword="));
        assertTrue(search.endsWith("&areas=I0100050"));
        assertFalse(search.contains("%EA%B0%9C%ED%8F%AC"));
        String alba = PartTimeSearchService.sourceUrl("ALBAHEAVEN", "", region, neighborhood, 2);
        assertTrue(alba.contains("WsSrchWord=&page=2"));
        assertTrue(alba.contains("hidArea="));
    }

    @Test void addressFilteringNeverIncludesOtherNeighborhoodsAndPreservesEmptyBatchCursor() {
        String row = "<li data-imid='%s'><span class='job-list__area'>%s</span><span class='job-list__subject'>모집</span></li>";
        String html = "<div>총 80건<div id='SearchJob'><ul id='jobNormal'>"
                + row.formatted("1", "서울 강남구 개포동") + row.formatted("2", "서울 강남구 개포1동")
                + row.formatted("3", "서울 강남구") + row.formatted("4", "서울 강남구 신개포동") + "</ul></div></div>";
        var result = PartTimeSearchService.parse("ALBAHEAVEN", html, 1);
        var filtered = PartTimeSearchService.filterNeighborhood(result, "개포동");
        assertEquals(List.of("1"), filtered.items().stream().map(PartTimeSearchService.Item::postingId).toList());
        assertEquals(-1, filtered.sites().getFirst().totalAvailable());
        var empty = PartTimeSearchService.filterNeighborhood(result, "역삼동");
        assertTrue(empty.items().isEmpty());
        assertEquals(2, empty.sites().getFirst().nextPage());
    }

    @Test void parsesObservedAreaPageFormatAndRejectsBlockPages() throws Exception {
        String html;
        try (var input = getClass().getResourceAsStream("/part-time/mon-area.html")) {
            html = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var result = PartTimeSearchService.parseMonArea(html, 1);
        assertFalse(result.items().isEmpty());
        assertFalse(result.items().getFirst().companyName().isBlank());
        assertFalse(result.items().getFirst().locations().isEmpty());
        assertTrue(result.sites().getFirst().totalAvailable() > 0);
        assertEquals(2, result.sites().getFirst().nextPage());
        assertThrows(Exception.class, () -> PartTimeSearchService.parseMonArea("<title>보안정책</title>", 1));
        String zero = "<script id='__NEXT_DATA__'>{\"props\":{\"pageProps\":{\"dehydratedState\":{\"queries\":[{\"state\":{\"data\":{\"base\":{\"pagination\":{\"totalCount\":0,\"size\":20},\"normal\":{\"collection\":[]}}}}}]}}}}</script>";
        assertNull(PartTimeSearchService.parseMonArea(zero, 1).sites().getFirst().nextPage());
    }
}
