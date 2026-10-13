package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CareerSearchCriteriaTest {

    @Test
    void treatsYearRangeAsExperiencedEvenWhenCareerTypeIsAny() {
        JobSearchRequest request = new JobSearchRequest(
                "Java", List.of(), "ANY", null, 2, "ANY", List.of()
        );

        assertThat(CareerSearchCriteria.effectiveType(request)).isEqualTo("EXPERIENCED");
    }

    @Test
    void keepsSelectedCareerTypeWhenYearRangeIsEmpty() {
        JobSearchRequest request = new JobSearchRequest(
                "Java", List.of(), "NO_PREFERENCE", null, null, "ANY", List.of()
        );

        assertThat(CareerSearchCriteria.effectiveType(request)).isEqualTo("NO_PREFERENCE");
    }
}
