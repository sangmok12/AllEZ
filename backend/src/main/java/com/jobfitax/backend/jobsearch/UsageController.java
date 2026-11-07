package com.jobfitax.backend.jobsearch;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UsageController {
    private final SearchAnalyticsRecorder recorder;

    public UsageController(SearchAnalyticsRecorder recorder) { this.recorder = recorder; }

    @PostMapping("/api/usage-events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void record(@Valid @RequestBody UsageEventRequest request) {
        recorder.recordUsage(event(request, Instant.now()));
    }

    static Map<String, Object> event(UsageEventRequest request, Instant now) {
        if (request.occurredAt().isBefore(now.minus(Duration.ofDays(1)))
                || request.occurredAt().isAfter(now.plusSeconds(300))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event time");
        }
        if ("search_click".equals(request.eventType())) {
            if (request.searchId() == null || request.criteria() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing search context");
            if ("part_time".equals(request.category())) PartTimeSearchService.validateCriteria(request.criteria());
            else if (request.criteria().keyword() == null || request.criteria().keyword().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing search context");
        }
        if ("posting_click".equals(request.eventType()) && (request.searchId() == null
                || request.source() == null || request.postingId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing posting context");
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", request.eventId());
        event.put("visitor_id", request.visitorId());
        event.put("session_id", request.sessionId());
        event.put("category", request.category());
        event.put("event_type", request.eventType());
        event.put("occurred_at", request.occurredAt().toString());
        event.put("search_id", request.searchId());
        if ("search_click".equals(request.eventType())) {
            var criteria = SearchAnalyticsRecorder.criteriaFields(request.criteria());
            String neighborhood = request.criteria().neighborhoodId();
            if ("part_time".equals(request.category()) && neighborhood != null && neighborhood.matches("[A-Z0-9]{8}"))
                criteria.put("neighborhood_id", neighborhood);
            event.put("search_criteria", criteria);
        }
        if ("posting_click".equals(request.eventType())) {
            event.put("source", request.source());
            event.put("posting_id", request.postingId());
        }
        return event;
    }
}
