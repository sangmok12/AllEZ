package com.jobfitax.backend.jobsearch;

import java.util.List;

public record JobSearchResponse(
        List<JobSearchItem> items,
        int totalReturned,
        List<SiteResult> sites,
        int totalAvailable
) {
    public JobSearchResponse(List<JobSearchItem> items, int totalReturned, List<SiteResult> sites) {
        this(items, totalReturned, sites, (int) Math.min(Integer.MAX_VALUE, sites.stream().mapToLong(SiteResult::totalAvailable).sum()));
    }
    public record SiteResult(
            String source,
            String sourceLabel,
            String status,
            int returnedCount,
            int totalAvailable,
            Integer nextPage,
            String message
    ) {
    }
}
