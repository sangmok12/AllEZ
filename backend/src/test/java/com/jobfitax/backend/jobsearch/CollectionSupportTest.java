package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CollectionSupportTest {
    private JobSearchResponse.SiteResult site(String source, String status, int total) {
        return new JobSearchResponse.SiteResult(source, source, status, 0, total, null, "");
    }

    @Test void estimatesEachSourcesNativePageSizeAndOnlyConfirmsLongSearches() {
        assertFalse(CollectionSupport.estimate(List.of(site("ALBAMON", "SUCCESS", 697), site("ALBAHEAVEN", "SUCCESS", 444))).confirmationRequired());
        var broad = CollectionSupport.estimate(List.of(site("JOBKOREA", "SUCCESS", 7352), site("SARAMIN", "SUCCESS", 6705)));
        assertEquals(14057, broad.total());
        assertTrue(broad.confirmationRequired());
        assertTrue(broad.maxMinutes() >= broad.minMinutes());
        assertThrows(IllegalStateException.class, () -> CollectionSupport.estimate(List.of(site("JOBKOREA", "FAILED", 0), site("SARAMIN", "SUCCESS", 0))));
    }

    @Test void retriesOneTransientSourceFailureWithoutTurningItIntoZeroResults() {
        var attempts = new AtomicInteger();
        var result = CollectionSupport.retry(() -> attempts.incrementAndGet() == 1
                ? List.of(site("SARAMIN", "FAILED", 0)) : List.of(site("SARAMIN", "SUCCESS", 12)), value -> value);
        assertEquals(2, attempts.get());
        assertEquals(12, result.getFirst().totalAvailable());
        assertThrows(IllegalStateException.class, () -> CollectionSupport.validateCursor(3, 3));
        assertThrows(IllegalStateException.class, () -> CollectionSupport.validateCursor(3, 2));
        CollectionSupport.validateCursor(3, 4);
    }

    @Test void concurrentHundredThousandItemCollectionsShareOneProcessingBudget() throws Exception {
        var searching = mock(JobSearchService.class);
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        when(searching.searchPageForCollection(any())).thenAnswer(call -> {
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                var items = IntStream.range(0, 100000).mapToObj(i -> new JobSearchItem("JOBKOREA", "잡코리아", "" + i,
                        "회사", "공고 " + i, List.of("서울"), "", "", List.of(), List.of(), "2026-11-22", "", "https://example.com/" + i, null)).toList();
                return new JobSearchResponse(items, items.size(), List.of(site("JOBKOREA", "SUCCESS", 100000), site("SARAMIN", "SUCCESS", 0)));
            } finally { active.decrementAndGet(); }
        });
        var service = new JobCollectionService(searching, null);
        var request = new JobSearchPageRequest(new JobSearchRequest("java", List.of(), "ANY", null, null, "ANY", List.of()), 0, 0);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var completed = new AtomicInteger();
            var tasks = IntStream.range(0, 3).mapToObj(i -> executor.submit(() -> service.collect(request, update -> {
                if (update.phase().equals("complete")) {
                    assertEquals(100000, update.result().items().size());
                    completed.incrementAndGet();
                }
            }))).toList();
            for (var task : tasks) task.get(45, TimeUnit.SECONDS);
            assertEquals(3, completed.get());
            assertEquals(1, maxActive.get());
        }
    }
}
