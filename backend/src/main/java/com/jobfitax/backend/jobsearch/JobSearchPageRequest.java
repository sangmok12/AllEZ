package com.jobfitax.backend.jobsearch;

/** Zero-based page per source; null skips a source that is already complete. */
public record JobSearchPageRequest(
        JobSearchRequest criteria,
        Integer jobKoreaPage,
        Integer saraminPage
) {
}
