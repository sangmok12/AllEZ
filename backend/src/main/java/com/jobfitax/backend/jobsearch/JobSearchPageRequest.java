package com.jobfitax.backend.jobsearch;

import java.util.UUID;

/** Zero-based page per source; null skips a source that is already complete. */
public record JobSearchPageRequest(
        JobSearchRequest criteria,
        Integer jobKoreaPage,
        Integer saraminPage,
        UUID searchId
) {
    public JobSearchPageRequest(JobSearchRequest criteria, Integer jobKoreaPage, Integer saraminPage) {
        this(criteria, jobKoreaPage, saraminPage, null);
    }
}
