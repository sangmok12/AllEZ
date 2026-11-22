package com.jobfitax.backend.jobsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;

@RestController
class PartTimeCollectionController {
    private final PartTimeCollectionService service;
    private final ObjectMapper mapper;
    PartTimeCollectionController(PartTimeCollectionService service, ObjectMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    @PostMapping(value = "/api/part-time-search/collect", produces = "application/x-ndjson")
    ResponseEntity<StreamingResponseBody> collect(@RequestBody PartTimeSearchService.Request request) {
        PartTimeSearchService.validateCriteria(request.criteria());
        StreamingResponseBody body = output -> service.collect(request, update -> {
            try {
                // Valid JSON whitespace keeps small progress frames from being held
                // until the final result by intermediaries with a 16 KiB read buffer.
                output.write(mapper.writeValueAsBytes(update));
                if (!update.phase().equals("complete")) output.write(" ".repeat(16 * 1024).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.write('\n');
                output.flush();
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-store, no-transform")
                .header("X-Accel-Buffering", "no").body(body);
    }
    @PostMapping("/api/part-time-search/estimate")
    CollectionSupport.Estimate estimate(@RequestBody PartTimeSearchService.Request request) {
        PartTimeSearchService.validateCriteria(request == null ? null : request.criteria());
        return service.estimate(request);
    }

}

@Service
class PartTimeCollectionService {
    record Update(String phase, int collected, Integer total, PartTimeSearchService.Response result, String message) {}
    private final PartTimeSearchService search;
    private final SearchAnalyticsRecorder analytics;

    PartTimeCollectionService(PartTimeSearchService search, SearchAnalyticsRecorder analytics) {
        this.search = search; this.analytics = analytics;
    }

    void collect(PartTimeSearchService.Request request, Consumer<Update> emit) {
        long started = System.nanoTime();
        Instant observedAt = Instant.now();
        Map<String, PartTimeSearchService.Item> items = new LinkedHashMap<>();
        Map<String, JobSearchResponse.SiteResult> sites = new LinkedHashMap<>();
        Integer total = null;
        boolean acquired = false, completed = false;
        try {
            emit.accept(new Update("checking", 0, null, null, "검색 결과를 확인하고 있어요"));
            CollectionSupport.acquire(() -> emit.accept(new Update("waiting", 0, null, null, "다른 검색을 처리 중입니다. 잠시만 기다려 주세요.")));
            acquired = true;
            Integer mon = 1, alba = 1;
            for (int batch = 0; mon != null || alba != null; batch++) {
                if (Duration.between(observedAt, Instant.now()).compareTo(CollectionSupport.MAX_DURATION) > 0)
                    throw new IllegalStateException("검색 처리 시간이 초과됐습니다. 조건을 좁혀 다시 시도해 주세요.");
                // Record only the completed collection, rather than each internal source page.
                var input = new PartTimeSearchService.Request(request.criteria(), mon, alba);
                var response = CollectionSupport.retry(() -> search.search(input), PartTimeSearchService.Response::sites);
                for (var site : response.sites()) {
                    if (!List.of("ALBAMON", "ALBAHEAVEN").contains(site.source()) || !site.status().equals("SUCCESS") || site.totalAvailable() < 0)
                        throw new IllegalStateException(site.sourceLabel() + " 공고를 모두 불러오지 못했습니다. 다시 검색해 주세요.");
                    CollectionSupport.validateCursor(site.source().equals("ALBAMON") ? mon : alba, site.nextPage());
                    sites.put(site.source(), site);
                }
                if (sites.size() != 2) throw new IllegalStateException("두 사이트의 검색 결과를 확인하지 못했습니다. 다시 검색해 주세요.");
                if (total == null) total = (int) Math.min(Integer.MAX_VALUE, sites.values().stream().mapToLong(JobSearchResponse.SiteResult::totalAvailable).sum());
                for (var item : response.items()) items.putIfAbsent(item.source() + ":" + item.postingId(), item);
                mon = sites.get("ALBAMON").nextPage();
                alba = sites.get("ALBAHEAVEN").nextPage();
                emit.accept(new Update("collecting", items.size(), total, null, "공고를 수집하고 있어요"));
            }
            emit.accept(new Update("sorting", items.size(), total, null, "최신순으로 정렬 중…"));
            Map<PartTimeSearchService.Item, Instant> dates = new HashMap<>();
            items.values().forEach(item -> dates.put(item, PostingTime.estimate(item.registeredDate(), observedAt)));
            var sorted = items.values().stream().sorted(Comparator
                    .comparing((PartTimeSearchService.Item item) -> dates.get(item))
                    .reversed().thenComparing(PartTimeSearchService.Item::source)
                    .thenComparing(PartTimeSearchService.Item::postingId)).toList();
            var completedSites = sites.values().stream().map(site -> {
                int count = (int) sorted.stream().filter(item -> item.source().equals(site.source())).count();
                return new JobSearchResponse.SiteResult(site.source(), site.sourceLabel(), "SUCCESS", count, count, null, "");
            }).toList();
            var result = new PartTimeSearchService.Response(sorted, sorted.size(), completedSites, total == null ? 0 : total);
            record(request, result, started);
            completed = true;
            emit.accept(new Update("complete", sorted.size(), total, result, ""));
        } catch (UncheckedIOException disconnected) {
            // Stop before requesting further source pages when the browser disconnects.
            if (!completed) recordFailure(request, sites, "검색 연결이 종료되었습니다.", started);
        } catch (Exception error) {
            String message = error instanceof org.springframework.web.server.ResponseStatusException status ? status.getReason()
                    : error instanceof IllegalStateException ? error.getMessage() : "공고 수집에 실패했습니다. 다시 검색해 주세요.";
            if (!completed) recordFailure(request, sites, message, started);
            emit.accept(new Update("error", items.size(), total, null, message));
        } finally { if (acquired) CollectionSupport.release(); }
    }

    CollectionSupport.Estimate estimate(PartTimeSearchService.Request request) {
        long started = System.nanoTime();
        var input = new PartTimeSearchService.Request(request.criteria(), 1, 1);
        try {
            return CollectionSupport.estimate(CollectionSupport.retry(() -> search.search(input), PartTimeSearchService.Response::sites).sites());
        } catch (RuntimeException error) {
            recordFailure(request, Map.of(), "검색 건수를 확인하지 못했습니다. 원본 사이트 연결을 다시 시도해 주세요.", started);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "검색 건수를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void record(PartTimeSearchService.Request request, PartTimeSearchService.Response result, long started) {
        if (analytics == null || request.searchId() == null) return;
        try { analytics.recordPartTime(request, result, (System.nanoTime() - started) / 1_000_000); }
        catch (RuntimeException ignored) { /* Telemetry cannot block results. */ }
    }

    private void recordFailure(PartTimeSearchService.Request request, Map<String, JobSearchResponse.SiteResult> sites,
                               String message, long started) {
        var failures = List.of("ALBAMON", "ALBAHEAVEN").stream().map(source -> {
            var site = sites.get(source);
            return new JobSearchResponse.SiteResult(source, source.equals("ALBAMON") ? "알바몬" : "알바천국",
                    "FAILED", 0, site == null ? 0 : site.totalAvailable(), null, message);
        }).toList();
        record(request, new PartTimeSearchService.Response(List.of(), 0, failures), started);
    }
}
