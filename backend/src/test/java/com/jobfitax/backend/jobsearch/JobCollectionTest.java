package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobCollectionTest {
    private final JobSearchService search = mock(JobSearchService.class);
    private final JobCollectionService service = new JobCollectionService(search, null);
    private final JobSearchRequest criteria = new JobSearchRequest("java", List.of(), "ANY", null, null, "ANY", List.of());
    private final JobSearchPageRequest request = new JobSearchPageRequest(criteria, 0, 0);

    private JobSearchItem item(String source, int id, String time) {
        return new JobSearchItem(source, source, "" + id, "회사", "공고", List.of(), "", "", List.of(), List.of(),
                time == null ? "" : time.substring(0, 10), "", "https://example.com/" + id, time == null ? null : OffsetDateTime.parse(time));
    }
    private JobSearchResponse.SiteResult site(String source, int total, Integer next) {
        return new JobSearchResponse.SiteResult(source, source, "SUCCESS", 0, total, next, "");
    }
    private JobSearchResponse response(List<JobSearchItem> rows, int koreaTotal, Integer koreaNext, int saraminTotal, Integer saraminNext) {
        return new JobSearchResponse(rows, rows.size(), List.of(site("JOBKOREA", koreaTotal, koreaNext), site("SARAMIN", saraminTotal, saraminNext)));
    }

    @Test void rejects2001BeforeAnyMorePagesAndAllowsExactly2000() {
        when(search.searchPageForCollection(any())).thenReturn(response(List.of(), 1001, 1, 1000, 1));
        var updates = new ArrayList<JobCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals("rejected", updates.getLast().phase());
        assertEquals(2001, updates.getLast().total());
        assertNull(updates.getLast().result());
        verify(search, times(1)).searchPageForCollection(any());
        reset(search);
        var rows = new ArrayList<JobSearchItem>();
        for (int i = 0; i < 1000; i++) { rows.add(item("JOBKOREA", i, null)); rows.add(item("SARAMIN", i, null)); }
        when(search.searchPageForCollection(any())).thenReturn(response(rows, 1000, null, 1000, null));
        updates.clear(); service.collect(request, updates::add);
        assertEquals("complete", updates.getLast().phase());
        assertEquals(2000, updates.getLast().result().items().size());
    }

    @Test void globallySortsLaterPagesAndSkipsTheCompletedSource() {
        when(search.searchPageForCollection(any())).thenReturn(
                response(List.of(item("JOBKOREA", 1, "2026-11-07T10:00:00+09:00"), item("SARAMIN", 1, "2026-11-08T00:00:00+09:00")), 2, 1, 1, null),
                new JobSearchResponse(List.of(item("JOBKOREA", 2, "2026-11-09T01:00:00Z")), 1, List.of(site("JOBKOREA", 2, null))));
        var updates = new ArrayList<JobCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals(List.of("checking", "collecting", "collecting", "sorting", "complete"), updates.stream().map(JobCollectionService.Update::phase).toList());
        var result = updates.getLast().result();
        assertEquals(List.of("2", "1", "1"), result.items().stream().map(JobSearchItem::postingId).toList());
        assertEquals(List.of("JOBKOREA", "SARAMIN", "JOBKOREA"), result.items().stream().map(JobSearchItem::source).toList());
        assertEquals(2, result.sites().stream().filter(s -> s.source().equals("JOBKOREA")).findFirst().orElseThrow().returnedCount());
        var calls = ArgumentCaptor.forClass(JobSearchPageRequest.class);
        verify(search, times(2)).searchPageForCollection(calls.capture());
        assertEquals(1, calls.getAllValues().get(1).jobKoreaPage());
        assertNull(calls.getAllValues().get(1).saraminPage());
        assertTrue(updates.subList(0, updates.size() - 1).stream().allMatch(u -> u.result() == null));
    }

    @Test void supportsAllFortyPagesWhenOneSourceHas2000Rows() {
        when(search.searchPageForCollection(any())).thenAnswer(call -> {
            JobSearchPageRequest input = call.getArgument(0);
            int page = input.jobKoreaPage();
            if (page > 0) assertNull(input.saraminPage());
            var rows = new ArrayList<JobSearchItem>();
            for (int i = 0; i < 50; i++) rows.add(item("JOBKOREA", page * 50 + i, null));
            return response(rows, 2000, page == 39 ? null : page + 1, 0, null);
        });
        var updates = new ArrayList<JobCollectionService.Update>(); service.collect(request, updates::add);
        assertEquals("complete", updates.getLast().phase());
        assertEquals(2000, updates.getLast().result().items().size());
        verify(search, times(40)).searchPageForCollection(any());
    }

    @Test void rejectsFailuresMissingRowsAndDuplicateOnlyPagesWithoutPublishingPartialResults() {
        var failed = new JobSearchResponse.SiteResult("SARAMIN", "사람인", "FAILED", 0, 0, 0, "오류");
        for (var value : List.of(
                new JobSearchResponse(List.of(), 0, List.of(site("JOBKOREA", 0, null), failed)),
                response(List.of(item("JOBKOREA", 1, null)), 2, null, 0, null),
                response(List.of(), 2, 1, 0, null))) {
            when(search.searchPageForCollection(any())).thenReturn(value);
            var updates = new ArrayList<JobCollectionService.Update>(); service.collect(request, updates::add);
            assertEquals("error", updates.getLast().phase());
            assertTrue(updates.stream().noneMatch(u -> u.phase().equals("complete")));
        }
    }

    @Test void stopsRequestingPagesOnDisconnectAndReleasesTheSlot() {
        when(search.searchPageForCollection(any())).thenReturn(response(List.of(item("JOBKOREA", 1, null)), 2, 1, 0, null));
        for (int i = 0; i < 4; i++) service.collect(request, update -> {
            if (update.phase().equals("collecting")) throw new UncheckedIOException(new IOException("disconnected"));
        });
        verify(search, times(4)).searchPageForCollection(any());
    }

    @Test void recordsOneAggregateWithAllSourceCountsAndTheOriginalSearchIdentity() {
        var analytics = mock(SearchAnalyticsRecorder.class);
        var collecting = new JobCollectionService(search, analytics);
        when(search.searchPageForCollection(any())).thenReturn(response(List.of(item("JOBKOREA", 1, null)), 1, null, 0, null));
        var identified = new JobSearchPageRequest(criteria, 0, 0, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        collecting.collect(identified, update -> {});
        var saved = ArgumentCaptor.forClass(JobSearchResponse.class);
        verify(analytics, times(1)).record(eq(identified), saved.capture(), anyLong());
        assertEquals(1, saved.getValue().totalReturned());
        assertEquals(1, saved.getValue().sites().getFirst().returnedCount());
    }
}
