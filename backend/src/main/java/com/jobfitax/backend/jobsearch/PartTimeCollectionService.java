package com.jobfitax.backend.jobsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
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
                output.write((mapper.writeValueAsString(update) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-store")
                .header("X-Accel-Buffering", "no").body(body);
    }
}

@Service
class PartTimeCollectionService {
    static final int LIMIT = 500;
    record Update(String phase, int collected, Integer total, PartTimeSearchService.Response result, String message) {}
    private final PartTimeSearchService search;
    private final SearchAnalyticsRecorder analytics;
    private final Semaphore slots = new Semaphore(3);

    PartTimeCollectionService(PartTimeSearchService search, SearchAnalyticsRecorder analytics) {
        this.search = search; this.analytics = analytics;
    }

    void collect(PartTimeSearchService.Request request, Consumer<Update> emit) {
        if (!slots.tryAcquire()) {
            emit.accept(new Update("error", 0, null, null, "검색 요청이 많습니다. 잠시 후 다시 시도해 주세요."));
            return;
        }
        long started = System.nanoTime();
        Instant observedAt = Instant.now();
        Map<String, PartTimeSearchService.Item> items = new LinkedHashMap<>();
        Map<String, JobSearchResponse.SiteResult> sites = new LinkedHashMap<>();
        Integer total = null;
        try {
            emit.accept(new Update("checking", 0, null, null, "검색 결과를 확인하고 있어요"));
            Integer mon = 1, alba = 1;
            for (int batch = 0; mon != null || alba != null; batch++) {
                if (batch >= 30 || Duration.between(observedAt, Instant.now()).toSeconds() > 180)
                    throw new IllegalStateException("수집 시간이 길어지고 있습니다. 검색 범위를 좁혀 다시 시도해 주세요.");
                // Record only the completed collection, rather than each internal source page.
                var response = search.search(new PartTimeSearchService.Request(request.criteria(), mon, alba));
                for (var site : response.sites()) {
                    if (!site.status().equals("SUCCESS"))
                        throw new IllegalStateException(site.sourceLabel() + " 공고를 모두 불러오지 못했습니다. 다시 검색해 주세요.");
                    sites.put(site.source(), site);
                }
                total = sites.values().stream().mapToInt(JobSearchResponse.SiteResult::totalAvailable).sum();
                if (total > LIMIT) {
                    recordFailure(request, sites, "검색 결과가 500건을 초과했습니다.", started);
                    emit.accept(new Update("rejected", 0, total, null,
                            "검색 결과가 500건을 넘어요. 동네나 검색어를 선택해 범위를 좁혀 주세요."));
                    return;
                }
                int before = items.size();
                for (var item : response.items()) items.putIfAbsent(item.source() + ":" + item.postingId(), item);
                if (items.size() > LIMIT) throw new IllegalStateException("검색 결과가 변경되었습니다. 범위를 좁혀 다시 검색해 주세요.");
                mon = sites.get("ALBAMON").nextPage();
                alba = sites.get("ALBAHEAVEN").nextPage();
                emit.accept(new Update("collecting", items.size(), total, null, "공고를 수집하고 있어요"));
                if (items.size() == before && (mon != null || alba != null))
                    throw new IllegalStateException("목록이 변경되어 전체 공고를 확인하지 못했습니다. 다시 검색해 주세요.");
            }
            for (var site : sites.values()) {
                long count = items.values().stream().filter(item -> item.source().equals(site.source())).count();
                if (count != site.totalAvailable())
                    throw new IllegalStateException("수집 중 목록이 변경되었습니다. 전체 공고를 확인하려면 다시 검색해 주세요.");
            }
            emit.accept(new Update("sorting", items.size(), total, null, "최신순으로 정렬 중…"));
            Map<PartTimeSearchService.Item, Instant> dates = new HashMap<>();
            items.values().forEach(item -> dates.put(item, PostingTime.estimate(item.registeredDate(), observedAt)));
            var sorted = items.values().stream().sorted(Comparator
                    .comparing((PartTimeSearchService.Item item) -> dates.get(item))
                    .reversed().thenComparing(PartTimeSearchService.Item::source)
                    .thenComparing(PartTimeSearchService.Item::postingId)).toList();
            var result = new PartTimeSearchService.Response(sorted, sorted.size(), List.copyOf(sites.values()));
            record(request, result, started);
            emit.accept(new Update("complete", sorted.size(), total, result, ""));
        } catch (UncheckedIOException disconnected) {
            // Stop before requesting further source pages when the browser disconnects.
            recordFailure(request, sites, "검색 연결이 종료되었습니다.", started);
        } catch (Exception error) {
            String message = error instanceof IllegalStateException ? error.getMessage() : "공고 수집에 실패했습니다. 다시 검색해 주세요.";
            recordFailure(request, sites, message, started);
            emit.accept(new Update("error", items.size(), total, null, message));
        } finally { slots.release(); }
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
