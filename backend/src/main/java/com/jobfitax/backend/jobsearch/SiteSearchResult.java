package com.jobfitax.backend.jobsearch;

import java.util.List;

record SiteSearchResult(
        String source,
        String sourceLabel,
        List<JobSearchItem> items,
        int totalAvailable,
        Integer nextPage,
        String errorMessage
) {
    static SiteSearchResult failed(String source, String sourceLabel, int page, String errorMessage) {
        return new SiteSearchResult(source, sourceLabel, List.of(), 0, page, errorMessage);
    }
}
