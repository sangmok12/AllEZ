package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.ObjectMapper;

class SaraminSearchClientCriteriaTest {
    private final SaraminSearchClient client = new SaraminSearchClient(new ObjectMapper());

    private MultiValueMap<String, String> params(String career, Integer minimum, Integer maximum) {
        var request = new JobSearchRequest("java", List.of("I000"), career, minimum, maximum, "ANY", List.of());
        return UriComponentsBuilder.fromUri(client.buildUri(request, List.of("101000"), 1)).build().getQueryParams();
    }

    @Test
    void allCareersMustNotBecomeNoPreferenceOnly() {
        for (String career : new String[] {"ANY", null}) {
            var query = params(career, null, null);
            assertThat(query).doesNotContainKeys("exp_none", "exp_cd", "exp_min", "exp_max");
            assertThat(query.getFirst("searchword")).isEqualTo("java");
            assertThat(query.getFirst("loc_cd")).isEqualTo("101000");
            assertThat(query.getFirst("recruitPageCount")).isEqualTo("50");
        }
    }

    @Test
    void noPreferenceMustNotSendAnUnsupportedCareerCodeThatBroadensResults() {
        var query = params("NO_PREFERENCE", null, null);
        assertThat(query.getFirst("exp_none")).isEqualTo("y");
        assertThat(query).doesNotContainKeys("exp_cd", "exp_min", "exp_max");
    }

    @Test
    void newcomerAndExperiencedSelectionsKeepTheirNativeCodes() {
        assertThat(params("NEWCOMER", null, null).getFirst("exp_cd")).isEqualTo("1");
        assertThat(params("EXPERIENCED", null, null).getFirst("exp_cd")).isEqualTo("2");
        assertThat(params("NEWCOMER", null, null)).doesNotContainKey("exp_none");
        assertThat(params("EXPERIENCED", null, null)).doesNotContainKey("exp_none");
    }

    @Test
    void careerYearBoundsImplyExperiencedWithoutAddingNoPreference() {
        var query = params("ANY", 3, 7);
        assertThat(query.getFirst("exp_cd")).isEqualTo("2");
        assertThat(query.getFirst("exp_min")).isEqualTo("3");
        assertThat(query.getFirst("exp_max")).isEqualTo("7");
        assertThat(query).doesNotContainKey("exp_none");
    }
}
