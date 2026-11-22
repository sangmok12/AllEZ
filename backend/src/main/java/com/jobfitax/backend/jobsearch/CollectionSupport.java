package com.jobfitax.backend.jobsearch;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared memory budget for large collections; queued searches keep streaming progress. */
final class CollectionSupport {
    static final Duration MAX_DURATION = Duration.ofHours(2);
    private static final Semaphore SLOT = new Semaphore(1, true);
    record Estimate(int total, int minMinutes, int maxMinutes, boolean confirmationRequired) {}

    static void acquire(Runnable waiting) {
        try {
            while (!SLOT.tryAcquire(5, TimeUnit.SECONDS)) waiting.run();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("검색 대기가 중단됐습니다. 다시 시도해 주세요.");
        }
    }
    static void release() { SLOT.release(); }

    static <T> T retry(Supplier<T> request, Function<T, List<JobSearchResponse.SiteResult>> sites) {
        T result = request.get();
        if (sites.apply(result).stream().allMatch(s -> "SUCCESS".equals(s.status()))) return result;
        try { Thread.sleep(300); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); return result; }
        return request.get();
    }

    static Estimate estimate(List<JobSearchResponse.SiteResult> sites) {
        if (sites.size() != 2 || sites.stream().anyMatch(s -> !"SUCCESS".equals(s.status()) || s.totalAvailable() < 0))
            throw new IllegalStateException("검색 건수를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        long total = sites.stream().mapToLong(JobSearchResponse.SiteResult::totalAvailable).sum();
        int batches = sites.stream().mapToInt(s -> (int) Math.ceil(s.totalAvailable() / (s.source().equals("ALBAMON") ? 20.0 : 50.0))).max().orElse(0);
        // A conservative range, not a completion-time promise. Native page latencies vary.
        return new Estimate((int) Math.min(Integer.MAX_VALUE, total), Math.max(1, (int) Math.ceil(batches * .75 / 60)),
                Math.max(1, (int) Math.ceil(batches * 1.5 / 60)), batches >= 60);
    }

    static void validateCursor(Integer current, Integer next) {
        if (next != null && (current == null || next <= current))
            throw new IllegalStateException("원본 사이트의 페이지 정보를 확인하지 못했습니다. 다시 검색해 주세요.");
    }
}
