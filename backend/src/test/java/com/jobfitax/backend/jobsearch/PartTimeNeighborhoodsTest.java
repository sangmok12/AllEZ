package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class PartTimeNeighborhoodsTest {
    @Test void completedSearchRecordsItsResultButAnalyticsFailureCannotBreakSearch() {
        var analytics = org.mockito.Mockito.mock(SearchAnalyticsRecorder.class);
        var criteria = new JobSearchRequest("", List.of("B000_B070"), "ANY", null, null, "ANY", List.of());
        var request = new PartTimeSearchService.Request(criteria, 1, 1, java.util.UUID.randomUUID(), null, null);
        var service = new PartTimeSearchService(new JobSearchRegionService(new ObjectMapper()),
            new PartTimeNeighborhoods(code -> "[]"), url -> "<title>blocked</title>", analytics);
        var result = service.search(request);
        org.mockito.Mockito.verify(analytics).recordPartTime(org.mockito.ArgumentMatchers.eq(request),
            org.mockito.ArgumentMatchers.eq(result), org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.doThrow(new IllegalStateException("writer unavailable")).when(analytics)
            .recordPartTime(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        assertDoesNotThrow(() -> service.search(request));
    }

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
        assertTrue(alba.contains("/job/area/mainlocal?"));
        assertTrue(alba.contains("&page=2"));
        assertFalse(alba.contains("hidArea="));
    }

    @Test void albaRequestMatchesTheUserSuppliedNativeInchangSearch() {
        var region = new PartTimeSearchService.Region("B070", "031", "구리시");
        var dong = new PartTimeNeighborhoods.Neighborhood("B0700130", "인창동");
        var uri = java.net.URI.create(PartTimeSearchService.sourceUrl("ALBAHEAVEN", "", region, dong, 1));
        assertEquals("/job/area/mainlocal", uri.getPath());
        var query = java.util.Arrays.stream(uri.getRawQuery().split("&")).map(part -> part.split("=", 2))
            .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts ->
                java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("031||구리시||인창동", query.get("strAreaMulti"));
        assertEquals("구리시", query.get("selGugun"));
        assertEquals("인창동", query.get("selDong"));
        assertEquals("031", query.get("sidocd"));
        assertEquals("50", query.get("hidSortCnt"));
        assertEquals("LIST", query.get("hidListView"));
        assertEquals("Y", query.get("hidSearchyn"));
        assertEquals("", query.get("schIncludeText"));
        String keyword = PartTimeSearchService.sourceUrl("ALBAHEAVEN", "카페", region, dong, 2);
        assertTrue(keyword.contains("schIncludeText=" + java.net.URLEncoder.encode("카페", java.nio.charset.StandardCharsets.UTF_8)));
        assertTrue(keyword.endsWith("page=2"));
    }

    @Test void nativeAlbaResultsKeepPostingsEvenWhenDisplayedAddressOmitsTheNeighborhood() {
        // Synthetic response: verifies lossless parsing, not a claim of a live source capture.
        var region = new PartTimeSearchService.Region("B070", "031", "구리시");
        var dong = new PartTimeNeighborhoods.Neighborhood("B0700130", "인창동");
        String row = "<tr><td class='local'>%s</td><td class='title'><a href='/job/Detail?adid=%s'>"
            + "<span class='company'>가게</span><span class='title'>모집</span></a></td>"
            + "<td class='pay'><span class='payIcon'>시급</span><span class='number'>12,000</span></td>"
            + "<td class='regDate'>11/07</td></tr>";
        String html = "<input name='strAreaMulti' value='031||구리시||인창동'>"
            + "<div id='NormalInfo'><p>총 51건</p><table><tbody>"
            + row.formatted("구리시 인창동", "1") + row.formatted("구리시", "2") + row.formatted("", "3")
            + "</tbody></table></div>";
        var result = PartTimeSearchService.parseAlbaArea(html, 1, region, dong);
        assertEquals(List.of("1", "2", "3"), result.items().stream().map(PartTimeSearchService.Item::postingId).toList());
        assertEquals("시급 12,000원", result.items().getFirst().payText());
        assertEquals(51, result.sites().getFirst().totalAvailable());
        assertEquals(2, result.sites().getFirst().nextPage());
        assertNull(PartTimeSearchService.parseAlbaArea(html, 2, region, dong).sites().getFirst().nextPage());
        assertThrows(IllegalStateException.class, () -> PartTimeSearchService.parseAlbaArea(
            html.replace("031||구리시||인창동", "031||구리시||전체"), 1, region, dong));
        assertThrows(IllegalStateException.class, () -> PartTimeSearchService.parseAlbaArea("<title>Blocked</title>", 1, region, dong));
        String zero = "<input name='strAreaMulti' value='031||구리시||인창동'><div id='NormalInfo'>총 0건<table><tbody></tbody></table></div>";
        assertTrue(PartTimeSearchService.parseAlbaArea(zero, 1, region, dong).items().isEmpty());
    }

    @Test void userCapturedInchangPagePreservesAllFiftySourcePostingsAndPagination() throws Exception {
        String html;
        List<String> sourceIds;
        try (var input = getClass().getResourceAsStream("/part-time/alba-inchang.html")) {
            html = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // Extracted independently from the original pasted HTML, not from the service parser.
        try (var input = getClass().getResourceAsStream("/part-time/alba-inchang-ids.txt")) {
            sourceIds = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        assertEquals(50, sourceIds.size());
        var region = new PartTimeSearchService.Region("B070", "031", "구리시");
        var dong = new PartTimeNeighborhoods.Neighborhood("B0700130", "인창동");
        var result = PartTimeSearchService.parseAlbaArea(html, 1, region, dong);
        assertEquals(sourceIds, result.items().stream().map(PartTimeSearchService.Item::postingId).toList());
        assertEquals(50, result.totalReturned());
        assertEquals(56, result.sites().getFirst().totalAvailable());
        assertEquals(2, result.sites().getFirst().nextPage());
        assertTrue(result.items().stream().allMatch(item -> !item.title().isBlank()
                && !item.companyName().isBlank() && !item.payText().isBlank()));
        var shortAddresses = org.jsoup.Jsoup.parse(html);
        shortAddresses.select("td.local").forEach(element -> element.text("구리시"));
        assertEquals(sourceIds, PartTimeSearchService.parseAlbaArea(shortAddresses.outerHtml(), 1, region, dong)
                .items().stream().map(PartTimeSearchService.Item::postingId).toList());
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
