package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SearchPaginationTest {
    @Test
    void continuesPastTheFormerFivePageLimit() {
        assertThat(SearchPagination.nextPage(5, 50, 1200, 50)).isEqualTo(6);
        assertThat(SearchPagination.nextPage(20, 50, 1200, 50)).isEqualTo(21);
    }

    @Test
    void stopsOnTheActualFinalPageIncludingExactMultiples() {
        assertThat(SearchPagination.nextPage(23, 50, 1200, 50)).isNull();
        assertThat(SearchPagination.nextPage(2, 39, 139, 50)).isNull();
        assertThat(SearchPagination.nextPage(0, 0, 0, 50)).isNull();
    }

    @Test
    void usesPageLengthWhenUpstreamTotalIsUnavailable() {
        assertThat(SearchPagination.nextPage(7, 50, 0, 50)).isEqualTo(8);
        assertThat(SearchPagination.nextPage(8, 12, 0, 50)).isNull();
    }
}
