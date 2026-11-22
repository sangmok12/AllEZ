package com.jobfitax.backend.jobsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

@RestController
class JobCollectionController {
    private final JobCollectionService service;
    private final ObjectMapper mapper;

    JobCollectionController(JobCollectionService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/api/job-search/collect", produces = "application/x-ndjson")
    ResponseEntity<StreamingResponseBody> collect(@RequestBody JobSearchPageRequest request) {
        JobSearchService.validate(request == null ? null : request.criteria());
        StreamingResponseBody body = output -> service.collect(request, update -> {
            try {
                output.write(mapper.writeValueAsBytes(update));
                if (!update.phase().equals("complete")) output.write(" ".repeat(16 * 1024).getBytes(StandardCharsets.UTF_8));
                output.write('\n');
                output.flush();
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-store, no-transform").header("X-Accel-Buffering", "no").body(body);
    }
    @PostMapping("/api/job-search/estimate")
    CollectionSupport.Estimate estimate(@RequestBody JobSearchPageRequest request) {
        JobSearchService.validate(request == null ? null : request.criteria());
        return service.estimate(request);
    }

}

@Service
class JobCollectionService {
    private static final List<String> SOURCES = List.of("JOBKOREA", "SARAMIN");
    record Update(String phase, int collected, Integer total, JobSearchResponse result, String message) {}
    private final JobSearchService search;
    private final SearchAnalyticsRecorder analytics;

    JobCollectionService(JobSearchService search, SearchAnalyticsRecorder analytics) {
        this.search = search;
        this.analytics = analytics;
    }

    void collect(JobSearchPageRequest request, Consumer<Update> emit) {
        long started = System.nanoTime();
        Instant observedAt = Instant.now();
        Map<String, JobSearchItem> items = new LinkedHashMap<>();
        Map<String, JobSearchResponse.SiteResult> sites = new LinkedHashMap<>();
        Integer total = null;
        boolean acquired = false, completed = false;
        try {
            emit.accept(new Update("checking", 0, null, null, "검색 결과를 확인하고 있어요"));
            CollectionSupport.acquire(() -> emit.accept(new Update("waiting", 0, null, null, "다른 검색을 처리 중입니다. 잠시만 기다려 주세요.")));
            acquired = true;
            Integer korea = 0, saramin = 0;
            for (int batch = 0; korea != null || saramin != null; batch++) {
                if (Duration.between(observedAt, Instant.now()).compareTo(CollectionSupport.MAX_DURATION) > 0)
                    throw new IllegalStateException("검색 처리 시간이 초과됐습니다. 조건을 좁혀 다시 시도해 주세요.");
                var input = new JobSearchPageRequest(request.criteria(), korea, saramin);
                var response = CollectionSupport.retry(() -> search.searchPageForCollection(input), JobSearchResponse::sites);
                for (var site : response.sites()) {
                    if (!SOURCES.contains(site.source()) || !site.status().equals("SUCCESS") || site.totalAvailable() < 0)
                        throw new IllegalStateException(site.sourceLabel() + " 공고를 모두 확인하지 못했습니다. 다시 검색해 주세요.");
                    CollectionSupport.validateCursor(site.source().equals("JOBKOREA") ? korea : saramin, site.nextPage());
                    sites.put(site.source(), site);
                }
                if (sites.size() != 2) throw new IllegalStateException("두 사이트의 전체 검색 결과를 확인하지 못했습니다. 다시 검색해 주세요.");
                long declared = sites.values().stream().mapToLong(JobSearchResponse.SiteResult::totalAvailable).sum();
                if (total == null) total = (int) Math.min(Integer.MAX_VALUE, declared);
                for (var item : response.items()) items.putIfAbsent(item.source() + ":" + item.postingId(), item);
                korea = sites.get("JOBKOREA").nextPage();
                saramin = sites.get("SARAMIN").nextPage();
                emit.accept(new Update("collecting", items.size(), total, null, "공고를 수집하고 있어요"));
            }
            var completedSites = new ArrayList<JobSearchResponse.SiteResult>();
            for (var site : sites.values()) {
                int count = (int) items.values().stream().filter(item -> item.source().equals(site.source())).count();
                completedSites.add(new JobSearchResponse.SiteResult(site.source(), site.sourceLabel(), "SUCCESS", count, count, null, ""));
            }
            emit.accept(new Update("sorting", items.size(), total, null, "최신순으로 정렬 중…"));
            var sorted = items.values().stream().sorted(Comparator
                    .comparing((JobSearchItem item) -> item.sortDate() == null ? PostingTime.estimate(item.registeredDate(), observedAt) : item.sortDate().toInstant())
                    .reversed().thenComparing(JobSearchItem::source).thenComparing(JobSearchItem::postingId)).toList();
            var result = new JobSearchResponse(sorted, sorted.size(), List.copyOf(completedSites), total == null ? 0 : total);
            record(request, result, started);
            completed = true;
            emit.accept(new Update("complete", sorted.size(), total, result, ""));
        } catch (UncheckedIOException disconnected) {
            if (!completed) recordFailure(request, sites, "검색 연결이 종료되었습니다.", started);
        } catch (Exception error) {
            String message = error instanceof IllegalStateException ? error.getMessage() : "공고 수집에 실패했습니다. 다시 검색해 주세요.";
            if (!completed) recordFailure(request, sites, message, started);
            emit.accept(new Update("error", items.size(), total, null, message));
        } finally { if (acquired) CollectionSupport.release(); }
    }

    CollectionSupport.Estimate estimate(JobSearchPageRequest request) {
        long started = System.nanoTime();
        var input = new JobSearchPageRequest(request.criteria(), 0, 0);
        try {
            return CollectionSupport.estimate(CollectionSupport.retry(() -> search.searchPageForCollection(input), JobSearchResponse::sites).sites());
        } catch (RuntimeException error) {
            recordFailure(request, Map.of(), "검색 건수를 확인하지 못했습니다. 원본 사이트 연결을 다시 시도해 주세요.", started);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "검색 건수를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void record(JobSearchPageRequest request, JobSearchResponse result, long started) {
        if (analytics == null || request.searchId() == null) return;
        try {
            var initial = new JobSearchPageRequest(request.criteria(), 0, 0, request.searchId(), request.visitorId(), request.sessionId());
            analytics.record(initial, result, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException ignored) { /* Telemetry cannot block results. */ }
    }

    private void recordFailure(JobSearchPageRequest request, Map<String, JobSearchResponse.SiteResult> sites, String message, long started) {
        var failures = SOURCES.stream().map(source -> new JobSearchResponse.SiteResult(source,
                source.equals("JOBKOREA") ? "잡코리아" : "사람인", "FAILED", 0,
                sites.containsKey(source) ? sites.get(source).totalAvailable() : 0, null, message)).toList();
        record(request, new JobSearchResponse(List.of(), 0, failures), started);
    }
}
