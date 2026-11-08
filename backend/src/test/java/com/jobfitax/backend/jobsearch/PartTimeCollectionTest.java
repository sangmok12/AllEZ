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

    @Test void rejects1001BeforeRequestingAnyMorePagesAndAcceptsExactly1000() {
        when(search.search(any())).thenReturn(response(List.of(), 501, 2, 500, 2));
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals("rejected", updates.getLast().phase());
        assertEquals(1001, updates.getLast().total());
        verify(search, times(1)).search(any());

        reset(search);
        var all = new ArrayList<PartTimeSearchService.Item>();
        for (int i=0;i<500;i++) { all.add(item("ALBAMON", i, "1시간 전")); all.add(item("ALBAHEAVEN", i, "2시간 전")); }
        when(search.search(any())).thenReturn(response(all, 500, null, 500, null));
        updates.clear(); service.collect(request, updates::add);
        assertEquals("complete", updates.getLast().phase());
        assertEquals(1000, updates.getLast().result().items().size());
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

    @Test void collectsAllFiftyPagesWhenOneSourceHasOneThousandResults() {
        when(search.search(any())).thenAnswer(call -> {
            var input = (PartTimeSearchService.Request) call.getArgument(0);
            int page = input.albamonPage();
            var rows = new ArrayList<PartTimeSearchService.Item>();
            for (int i=0;i<20;i++) rows.add(item("ALBAMON", (page-1)*20+i, "1시간전"));
            return response(rows, 1000, page == 50 ? null : page+1, 0, null);
        });
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals("complete", updates.getLast().phase());
        assertEquals(1000, updates.getLast().result().items().size());
        verify(search,times(50)).search(any());
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

    private PartTimeSearchService.Request multi(List<JobSearchRequest.Location> locations) {
        var criteria = new JobSearchRequest("", locations.stream().map(JobSearchRequest.Location::regionId).toList(),
                "ANY", null, null, "ANY", List.of(), null, null, locations);
        return new PartTimeSearchService.Request(criteria, 1, 1);
    }

    @Test void multipleRegionsKeepDongPairsAndDeduplicateSharedPostingsBeforeSorting() {
        var input = multi(List.of(new JobSearchRequest.Location("B000_B070", "B0700130", "인창동"),
                new JobSearchRequest.Location("I000_I010", "I0100050", "개포동")));
        when(search.search(any())).thenAnswer(call -> {
            var criteria = ((PartTimeSearchService.Request) call.getArgument(0)).criteria();
            boolean guri = criteria.regionIds().getFirst().equals("B000_B070");
            assertEquals(guri ? "B0700130" : "I0100050", criteria.neighborhoodId());
            return response(List.of(item("ALBAMON",1,"1시간전"),item("ALBAMON",guri?2:3,"5분전"),
                    item("ALBAHEAVEN",1,"2시간전")),2,null,1,null);
        });
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(input, updates::add);
        assertEquals("complete",updates.getLast().phase());
        assertEquals(4,updates.getLast().total());
        assertEquals(4,updates.getLast().result().items().size());
        assertEquals("5분전",updates.getLast().result().items().getFirst().registeredDate());
        verify(search,times(2)).search(any());
    }

    @Test void combinedLimitIsCheckedBeforeAnySecondPage() {
        var input = multi(List.of(new JobSearchRequest.Location("B000_B070",null,null),
                new JobSearchRequest.Location("I000_I010",null,null)));
        when(search.search(any())).thenReturn(response(List.of(item("ALBAMON",1,"오늘")),600,2,0,null));
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(input, updates::add);
        assertEquals("rejected",updates.getLast().phase());
        assertEquals(1200,updates.getLast().total());
        verify(search,times(2)).search(any());
    }

    @Test void broadRegionsRemoveCoveredSelectionsAndForeignDongIsRejectedBeforeRequests() {
        var input = multi(List.of(new JobSearchRequest.Location("B000_B070","B0700130","인창동"),
                new JobSearchRequest.Location("B000_B070",null,null),new JobSearchRequest.Location("B000",null,null)));
        assertEquals(List.of("B000"),PartTimeLocations.scopes(input.criteria()).getFirst().regionIds());
        assertEquals(1,PartTimeLocations.scopes(input.criteria()).size());
        var updates = new ArrayList<PartTimeCollectionService.Update>();
        service.collect(multi(List.of(new JobSearchRequest.Location("B000_B070","I0100050","개포동"))),updates::add);
        assertEquals("error",updates.getLast().phase());
        verifyNoInteractions(search);
    }
}
