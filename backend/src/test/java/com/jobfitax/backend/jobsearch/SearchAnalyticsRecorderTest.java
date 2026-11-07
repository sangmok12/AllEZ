package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchAnalyticsRecorderTest {
    @Test void partTimeResultsKeepTheSearchIdentityAndUseTheirOwnCategory() {
        UUID searchId = UUID.randomUUID(), visitor = UUID.randomUUID(), session = UUID.randomUUID();
        var criteria = new JobSearchRequest("", List.of("B000_B070"), "ANY", null, null, "ANY", List.of(), "B0700130");
        var item = new PartTimeSearchService.Item("ALBAMON", "알바몬", "1", "업체", "공고", List.of(),
                "", "", List.of(), List.of(), "", "", "https://www.albamon.com/jobs/detail/1", "");
        var response = new PartTimeSearchService.Response(List.of(item), 1, List.of(
                new JobSearchResponse.SiteResult("ALBAMON", "알바몬", "SUCCESS", 1, 1, null, ""),
                new JobSearchResponse.SiteResult("ALBAHEAVEN", "알바천국", "FAILED", 0, 0, 1, "")));
        var first = SearchAnalyticsRecorder.partTimeEvent(new PartTimeSearchService.Request(criteria, 1, 1, searchId, visitor, session), response, 123);
        var next = SearchAnalyticsRecorder.partTimeEvent(new PartTimeSearchService.Request(criteria, null, 2, searchId, visitor, session), response, 15);
        assertThat(first).containsEntry("category", "part_time").containsEntry("search_id", searchId)
                .containsEntry("visitor_id", visitor).containsEntry("session_id", session)
                .containsEntry("returned_count", 1).containsEntry("outcome", "partial_failure")
                .containsEntry("keyword", "").containsEntry("event_type", "search");
        assertThat(first).containsEntry("jobkorea_count", 0).containsEntry("saramin_count", 0)
                .containsEntry("jobkorea_page", null).containsEntry("saramin_page", null);
        assertThat(next).containsEntry("event_type", "load_more").containsEntry("search_id", searchId);
        for (String status : List.of("SUCCESS", "FAILED")) {
            var empty = new PartTimeSearchService.Response(List.of(), 0, List.of(
                new JobSearchResponse.SiteResult("ALBAMON", "알바몬", status, 0, 0, null, "")));
            assertThat(SearchAnalyticsRecorder.partTimeEvent(new PartTimeSearchService.Request(criteria, 1, 1, searchId, visitor, session), empty, 1))
                .containsEntry("outcome", status.equals("SUCCESS") ? "success" : "failure").containsEntry("returned_count", 0);
        }
    }
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
