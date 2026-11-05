package com.jobfitax.backend.jobsearch;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UsageEventRequest(
        @NotNull UUID eventId,
        @NotNull UUID visitorId,
        @NotNull UUID sessionId,
        @NotNull @Pattern(regexp = "jobs|part_time|homes") String category,
        @NotNull @Pattern(regexp = "page_view|search_click|posting_click") String eventType,
        @NotNull Instant occurredAt,
        UUID searchId,
        @Pattern(regexp = "JOBKOREA|SARAMIN|ALBAMON|ALBAHEAVEN|ZIGBANG|DABANG|NAVER") String source,
        @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String postingId,
        JobSearchRequest criteria
) {}
