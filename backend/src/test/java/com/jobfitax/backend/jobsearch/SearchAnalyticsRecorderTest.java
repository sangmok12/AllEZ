package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchAnalyticsRecorderTest {
    private final JobSearchResponse response = new JobSearchResponse(List.of(), 0, List.of(
            new JobSearchResponse.SiteResult("JOBKOREA", "잡코리아", "SUCCESS", 0, 100, 1, ""),
            new JobSearchResponse.SiteResult("SARAMIN", "사람인", "FAILED", 0, 0, 0, "unavailable")
    ));

    @Test
    void redactsEmailsAndPhoneNumbersWithoutChangingOrdinaryKeywords() {
        assertThat(SearchAnalyticsRecorder.redactKeyword("Java dev@example.com 010-1234-5678"))
                .isEqualTo("Java [email] [phone]");
        assertThat(SearchAnalyticsRecorder.redactKeyword("Spring Boot Java")).isEqualTo("Spring Boot Java");
        assertThat(SearchAnalyticsRecorder.redactKeyword("+82 10 1234 5678")).isEqualTo("[phone]");
    }

    @Test
    void linksPaginationToTheOriginalSearchWithoutCollectingVisitorIdentifiers() {
        UUID searchId = UUID.randomUUID();
        var criteria = new JobSearchRequest("Java", List.of("I000", "not-an-id"), "ANY",
                null, 3, "UNIVERSITY", List.of("PERMANENT", "unexpected"));
        var first = SearchAnalyticsRecorder.event(new JobSearchPageRequest(criteria, 0, 0, searchId), response, 234);
        var more = SearchAnalyticsRecorder.event(new JobSearchPageRequest(criteria, 1, null, searchId), response, 12);
        assertThat(first.get("search_id")).isEqualTo(searchId);
        assertThat(more.get("search_id")).isEqualTo(searchId);
        assertThat(first.get("event_id")).isNotEqualTo(more.get("event_id"));
        assertThat(first.get("event_type")).isEqualTo("search");
        assertThat(more.get("event_type")).isEqualTo("load_more");
        assertThat(first.get("region_ids")).isEqualTo(List.of("I000"));
        assertThat(first.get("employment_types")).isEqualTo(List.of("PERMANENT"));
        assertThat(first.get("outcome")).isEqualTo("partial_failure");
        assertThat(first.get("duration_ms")).isEqualTo(234);
        assertThat(first).doesNotContainKeys("ip", "user_id", "browser_id", "email", "phone");
    }
}
