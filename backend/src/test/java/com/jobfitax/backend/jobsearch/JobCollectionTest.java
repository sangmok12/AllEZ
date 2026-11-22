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

    @Test void acceptsAboveOldLimitAndKeepsFirstTotalDespiteDuplicatesAndCountChanges() {
        when(search.searchPageForCollection(any())).thenReturn(
                response(List.of(item("JOBKOREA", 1, null), item("SARAMIN", 1, null)), 3000, 1, 1000, null),
                new JobSearchResponse(List.of(item("JOBKOREA", 1, null), item("JOBKOREA", 2, null)), 2,
                        List.of(site("JOBKOREA", 2998, null))));
        var updates = new ArrayList<JobCollectionService.Update>();
        service.collect(request, updates::add);
        assertEquals("complete", updates.getLast().phase());
        assertEquals(4000, updates.getLast().total());
        assertEquals(4000, updates.getLast().result().totalAvailable());
        assertEquals(3, updates.getLast().result().items().size());
        assertTrue(updates.stream().filter(u -> u.total() != null).allMatch(u -> u.total() == 4000));
        verify(search, times(2)).searchPageForCollection(any());
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

    @Test void recordsBroadSuccessSourceErrorsAndZeroResultsDistinctly() {
        var analytics = mock(SearchAnalyticsRecorder.class);
        var collecting = new JobCollectionService(search, analytics);
        var identified = new JobSearchPageRequest(criteria, 0, 0, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(search.searchPageForCollection(any())).thenReturn(response(List.of(item("JOBKOREA", 1, null)), 3000, null, 0, null));
        collecting.collect(identified, update -> {});
        var broad = ArgumentCaptor.forClass(JobSearchResponse.class);
        verify(analytics).record(eq(identified), broad.capture(), anyLong());
        assertEquals(3000, broad.getValue().totalAvailable());
        assertEquals("success", SearchAnalyticsRecorder.event(identified, broad.getValue(), 1).get("outcome"));
        verifyNoMoreInteractions(analytics);

        clearInvocations(analytics);
        var failed = new JobSearchResponse.SiteResult("SARAMIN", "사람인", "FAILED", 0, 0, null, "오류");
        when(search.searchPageForCollection(any())).thenReturn(new JobSearchResponse(List.of(), 0, List.of(site("JOBKOREA", 0, null), failed)));
        collecting.collect(identified, update -> {});
        var saved = ArgumentCaptor.forClass(JobSearchResponse.class);
        verify(analytics).record(eq(identified), saved.capture(), anyLong());
        assertEquals("failure", SearchAnalyticsRecorder.event(identified, saved.getValue(), 1).get("outcome"));
        verifyNoMoreInteractions(analytics);

        clearInvocations(analytics);
        when(search.searchPageForCollection(any())).thenReturn(response(List.of(), 0, null, 0, null));
        collecting.collect(identified, update -> {});
        verify(analytics).record(eq(identified), saved.capture(), anyLong());
        assertEquals("success", SearchAnalyticsRecorder.event(identified, saved.getValue(), 1).get("outcome"));
        verifyNoMoreInteractions(analytics);
    }
}
