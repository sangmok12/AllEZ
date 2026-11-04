package com.jobfitax.backend.jobsearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SearchAnalyticsRecorder {
    private static final Logger log = LoggerFactory.getLogger(SearchAnalyticsRecorder.class);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+82[-.\\s]?)?0?[0-9]{1,2}[-.\\s]?[0-9]{3,4}[-.\\s]?[0-9]{4}(?![0-9])");
    private static final Set<String> CAREERS = Set.of("ANY", "NEWCOMER", "EXPERIENCED", "NO_PREFERENCE");
    private static final Set<String> EDUCATIONS = Set.of("ANY", "NO_PREFERENCE", "HIGH_SCHOOL", "COLLEGE", "UNIVERSITY", "MASTER", "DOCTOR");
    private static final Set<String> EMPLOYMENTS = Set.of("PERMANENT", "CONTRACT", "INTERN", "DISPATCH", "FREELANCE", "PART_TIME");

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String key;
    private final URI endpoint;
    private final ThreadPoolExecutor writer;

    public SearchAnalyticsRecorder(
            @Value("${SUPABASE_URL:}") String url,
            @Value("${SUPABASE_SECRET_KEY:}") String key,
            ObjectMapper mapper
    ) {
        this.mapper = mapper;
        this.key = key.strip();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        URI configured = null;
        if (!url.isBlank() && !this.key.isBlank()) {
            try {
                URI base = URI.create(url.strip().replaceAll("/+$", ""));
                if ("https".equals(base.getScheme()) && base.getHost() != null
                        && base.getHost().endsWith(".supabase.co")
                        && base.getUserInfo() == null && base.getQuery() == null
                        && (base.getPath().isEmpty() || "/".equals(base.getPath()))) {
                    configured = base.resolve("/rest/v1/search_events");
                }
            } catch (IllegalArgumentException ignored) {
                // Configuration errors never prevent search from starting.
            }
        }
        this.endpoint = configured;
        this.writer = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(128), runnable -> {
                    Thread thread = new Thread(runnable, "search-analytics-writer");
                    thread.setDaemon(true);
                    return thread;
                });
        if (endpoint == null) log.info("Search analytics disabled: server connection is not configured.");
    }

    public void record(JobSearchPageRequest request, JobSearchResponse response, long durationMs) {
        if (endpoint == null || response.sites().isEmpty()) return;
        Map<String, Object> event = event(request, response, durationMs);
        try {
            writer.execute(() -> write(event));
        } catch (RejectedExecutionException ignored) {
            log.warn("Search analytics queue full or stopping; event was not stored.");
        }
    }

    private void write(Map<String, Object> event) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(3))
                    .header("apikey", key)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(event)))
                    .build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) log.warn("Search analytics storage returned HTTP {}.", status);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Never log exception messages, request bodies or headers: they can contain secrets.
            log.warn("Search analytics storage unavailable; search result was not affected.");
        }
    }

    static Map<String, Object> event(JobSearchPageRequest page, JobSearchResponse response, long durationMs) {
        JobSearchRequest criteria = page.criteria();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_id", UUID.randomUUID());
        event.put("search_id", page.searchId() == null ? UUID.randomUUID() : page.searchId());
        event.put("category", "jobs");
        boolean first = Integer.valueOf(0).equals(page.jobKoreaPage()) && Integer.valueOf(0).equals(page.saraminPage());
        event.put("event_type", first ? "search" : "load_more");
        event.put("keyword", redactKeyword(criteria.keyword()));
        event.put("region_ids", criteria.regionIds() == null ? List.of() : criteria.regionIds().stream()
                .filter(value -> value != null && value.matches("[A-Z0-9]{4}(?:_[A-Z0-9]{4})?")).distinct().limit(300).toList());
        event.put("career_type", safeEnum(criteria.careerType(), CAREERS));
        event.put("minimum_career_years", criteria.minimumCareerYears());
        event.put("maximum_career_years", criteria.maximumCareerYears());
        event.put("minimum_education", safeEnum(criteria.minimumEducation(), EDUCATIONS));
        event.put("employment_types", criteria.employmentTypes() == null ? List.of() : criteria.employmentTypes().stream()
                .filter(value -> value != null && EMPLOYMENTS.contains(value)).distinct().limit(6).toList());
        event.put("jobkorea_page", page.jobKoreaPage());
        event.put("saramin_page", page.saraminPage());
        event.put("returned_count", response.items().size());
        event.put("jobkorea_count", response.sites().stream().filter(site -> "JOBKOREA".equals(site.source()))
                .mapToInt(JobSearchResponse.SiteResult::returnedCount).sum());
        event.put("saramin_count", response.sites().stream().filter(site -> "SARAMIN".equals(site.source()))
                .mapToInt(JobSearchResponse.SiteResult::returnedCount).sum());
        long failures = response.sites().stream().filter(site -> "FAILED".equals(site.status())).count();
        event.put("outcome", failures == 0 ? "success" : failures == response.sites().size() ? "failure" : "partial_failure");
        event.put("duration_ms", (int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
        return event;
    }

    private static String safeEnum(String value, Set<String> allowed) {
        return value != null && allowed.contains(value) ? value : "ANY";
    }

    static String redactKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.strip().replaceAll("[\\p{Cntrl}]", " ");
        value = EMAIL.matcher(value).replaceAll("[email]");
        value = PHONE.matcher(value).replaceAll("[phone]");
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    @PreDestroy
    public void close() {
        writer.shutdown();
    }
}
