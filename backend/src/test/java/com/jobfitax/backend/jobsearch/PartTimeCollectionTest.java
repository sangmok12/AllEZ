package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PartTimeCollectionTest {
    private final PartTimeSearchService search = mock(PartTimeSearchService.class);
    private final PartTimeCollectionService service = new PartTimeCollectionService(search, null);
    private final PartTimeSearchService.Request request = new PartTimeSearchService.Request(
            new JobSearchRequest("카페", List.of(), "ANY", null, null, "ANY", List.of()), 1, 1);

    private PartTimeSearchService.Item item(String source, int id, String date) {
        return new PartTimeSearchService.Item(source, source, "" + id, "가게", "공고", List.of(), "", "",
                List.of(), List.of(), date, "", "https://example.com/" + id, "");
    }
    private JobSearchResponse.SiteResult site(String source, int total, Integer next) {
        return new JobSearchResponse.SiteResult(source, source, "SUCCESS", 0, total, next, "");
    }
    private PartTimeSearchService.Response response(List<PartTimeSearchService.Item> items, int monTotal, Integer monNext, int albaTotal, Integer albaNext) {
        return new PartTimeSearchService.Response(items, items.size(), List.of(site("ALBAMON", monTotal, monNext), site("ALBAHEAVEN", albaTotal, albaNext)));
    }

    @Test void rejects501BeforeRequestingAnyMorePagesAndAcceptsExactly500() {
        when(search.search(any())).thenReturn(response(List.of(), 251, 2, 250, 2));
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals("rejected", updates.getLast().phase());
        assertEquals(501, updates.getLast().total());
        verify(search, times(1)).search(any());

        reset(search);
        var all = new ArrayList<PartTimeSearchService.Item>();
        for (int i=0;i<250;i++) { all.add(item("ALBAMON", i, "1시간 전")); all.add(item("ALBAHEAVEN", i, "2시간 전")); }
        when(search.search(any())).thenReturn(response(all, 250, null, 250, null));
        updates.clear(); service.collect(request, updates::add);
        assertEquals("complete", updates.getLast().phase());
        assertEquals(500, updates.getLast().result().items().size());
    }

    @Test void collectsEveryPageBeforeSortingIncludingNewerItemsOnLaterPages() {
        when(search.search(any())).thenReturn(
                response(List.of(item("ALBAMON",1,"3시간전"),item("ALBAHEAVEN",1,"2시간전")),2,2,1,null),
                new PartTimeSearchService.Response(List.of(item("ALBAMON",2,"5분전")),1,List.of(site("ALBAMON",2,null))));
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals(List.of("checking","collecting","collecting","sorting","complete"),updates.stream().map(PartTimeCollectionService.Update::phase).toList());
        assertEquals(List.of("2","1","1"), updates.getLast().result().items().stream().map(PartTimeSearchService.Item::postingId).toList());
        var requests = org.mockito.ArgumentCaptor.forClass(PartTimeSearchService.Request.class);
        verify(search,times(2)).search(requests.capture());
        assertNull(requests.getAllValues().get(1).albaheavenPage());
    }

    @Test void failsClosedForSourceFailureCountChangesAndDuplicateOnlyPages() {
        var failed = new JobSearchResponse.SiteResult("ALBAHEAVEN","알바천국","FAILED",0,0,1,"오류");
        for (var result : List.of(
                new PartTimeSearchService.Response(List.of(),0,List.of(site("ALBAMON",0,null),failed)),
                response(List.of(item("ALBAMON",1,"오늘")),2,null,0,null),
                response(List.of(),2,2,0,null))) {
            when(search.search(any())).thenReturn(result);
            var updates = new ArrayList<PartTimeCollectionService.Update>();
            service.collect(request, updates::add);
            assertEquals("error",updates.getLast().phase());
            assertTrue(updates.stream().noneMatch(u -> u.phase().equals("complete")));
        }
    }

    @Test void datesUseKoreanTimeYearRolloverAndStablePrecision() {
        var now = Instant.parse("2026-01-01T01:00:00Z"); // 10:00 KST
        assertEquals(now.minusSeconds(41*60),PostingTime.estimate("41분 전",now));
        assertEquals(now.minusSeconds(22*3600),PostingTime.estimate("22시간 전 요약보기",now));
        assertEquals(Instant.parse("2025-12-30T15:00:00Z"),PostingTime.estimate("12/31",now));
        assertEquals(Instant.parse("2026-01-01T00:30:00Z"),PostingTime.estimate("2026-01-01 09:30",now));
        assertEquals(Instant.MIN,PostingTime.estimate("미정",now));
        assertEquals(Instant.MIN,PostingTime.estimate("13/99",now));
    }
}
