package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class UsageControllerTest {
    private final Instant now = Instant.parse("2026-11-05T09:00:00Z");
    private UsageEventRequest request(String type, String category, Instant at, UUID searchId,
                                      String source, String posting, JobSearchRequest criteria) {
        return new UsageEventRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                category, type, at, searchId, source, posting, criteria);
    }

    @Test void preservesCategoryAndAnonymousContextForAllThreeSections() {
        for (String category : List.of("jobs", "part_time", "homes")) {
            var request = request("page_view", category, now, null, null, null, null);
            var event = UsageController.event(request, now);
            assertThat(event.get("visitor_id")).isEqualTo(request.visitorId());
            assertThat(event.get("session_id")).isEqualTo(request.sessionId());
            assertThat(event.get("category")).isEqualTo(category);
            assertThat(event).doesNotContainKeys("ip", "email", "url", "user_agent");
        }
    }

    @Test void validatesContextsAndRedactsSearchHistory() {
        var criteria = new JobSearchRequest("Java dev@example.com", List.of("I000"), "ANY",
                null, null, "ANY", List.of());
        var search = request("search_click", "jobs", now, UUID.randomUUID(), null, null, criteria);
        assertThat(UsageController.event(search, now).get("search_criteria").toString())
                .contains("Java [email]").doesNotContain("dev@example.com");
        assertThatThrownBy(() -> UsageController.event(request("posting_click", "jobs", now,
                null, null, null, null), now)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> UsageController.event(request("page_view", "jobs",
                now.minusSeconds(86401), null, null, null, null), now))
                .isInstanceOf(ResponseStatusException.class);
        var recorder = mock(SearchAnalyticsRecorder.class);
        var current = request("page_view", "jobs", Instant.now(), null, null, null, null);
        new UsageController(recorder).record(current);
        verify(recorder).recordUsage(anyMap());
    }
}
