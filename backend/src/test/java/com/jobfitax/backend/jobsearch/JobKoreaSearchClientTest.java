package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobKoreaSearchClientTest {

    @Test
    void formatsKnownCareerRangeAsYears() {
        assertThat(JobKoreaSearchClient.careerLabel("2", "2")).isEqualTo("경력 2년");
    }

    @Test
    void formatsUnspecifiedExperiencedSentinelWithoutShowingOneHundredYears() {
        assertThat(JobKoreaSearchClient.careerLabel("2", "100")).isEqualTo("경력");
    }
}
