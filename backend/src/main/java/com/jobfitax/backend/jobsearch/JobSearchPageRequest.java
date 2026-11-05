package com.jobfitax.backend.jobsearch;

import java.util.UUID;

/** Zero-based page per source; null skips a completed source. */
public record JobSearchPageRequest(
        JobSearchRequest criteria, Integer jobKoreaPage, Integer saraminPage,
        UUID searchId, UUID visitorId, UUID sessionId
) {
    public JobSearchPageRequest(JobSearchRequest criteria, Integer jobKoreaPage, Integer saraminPage, UUID searchId) {
        this(criteria, jobKoreaPage, saraminPage, searchId, null, null);
    }
    public JobSearchPageRequest(JobSearchRequest criteria, Integer jobKoreaPage, Integer saraminPage) {
        this(criteria, jobKoreaPage, saraminPage, null);
    }
}
