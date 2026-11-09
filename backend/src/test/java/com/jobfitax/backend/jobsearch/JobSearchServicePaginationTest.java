package com.jobfitax.backend.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class JobSearchServicePaginationTest {
    private final JobSearchRegionService regions = mock(JobSearchRegionService.class);
    private final JobKoreaSearchClient jobKorea = mock(JobKoreaSearchClient.class);
    private final SaraminSearchClient saramin = mock(SaraminSearchClient.class);
    private final SearchAnalyticsRecorder analytics = mock(SearchAnalyticsRecorder.class);
    private final JobSearchService service = new JobSearchService(regions, jobKorea, saramin, analytics);
    private final JobSearchRequest criteria =
            new JobSearchRequest("Java", List.of(), "ANY", null, null, "ANY", List.of());

    private void resolveRegions() {
        when(regions.resolve(List.of())).thenReturn(new JobSearchRegionService.RegionCodes(List.of(), List.of()));
    }

    @Test
    void requestsLaterPagesAndSkipsCompletedSource() {
        resolveRegions();
        when(jobKorea.searchPage(criteria, List.of(), 6))
                .thenReturn(new SiteSearchResult("JOBKOREA", "잡코리아", List.of(), 1200, 7, ""));
        var result = service.searchPage(new JobSearchPageRequest(criteria, 6, null));
        verify(jobKorea).searchPage(criteria, List.of(), 6);
        verifyNoInteractions(saramin);
        assertThat(result.sites()).hasSize(1);
        assertThat(result.sites().getFirst().nextPage()).isEqualTo(7);
    }

    @Test
    void retriesFailureWithoutSkippingItsCursorWhileOtherSourceCompletes() {
        resolveRegions();
        when(jobKorea.searchPage(criteria, List.of(), 8)).thenThrow(new IllegalStateException("network"));
        when(saramin.searchPage(criteria, List.of(), 2))
                .thenReturn(new SiteSearchResult("SARAMIN", "사람인", List.of(), 100, null, ""));
        var result = service.searchPage(new JobSearchPageRequest(criteria, 8, 2));
        assertThat(result.sites().get(0).status()).isEqualTo("FAILED");
        assertThat(result.sites().get(0).nextPage()).isEqualTo(8);
        assertThat(result.sites().get(1).status()).isEqualTo("SUCCESS");
        assertThat(result.sites().get(1).nextPage()).isNull();
    }

    @Test
    void analyticsFailureDoesNotFailSearchResponse() {
        resolveRegions();
        when(jobKorea.searchPage(criteria, List.of(), 0))
                .thenReturn(new SiteSearchResult("JOBKOREA", "잡코리아", List.of(), 0, null, ""));
        doThrow(new IllegalStateException("storage unavailable")).when(analytics)
                .record(any(), any(), anyLong());
        var result = service.searchPage(new JobSearchPageRequest(criteria, 0, null));
        assertThat(result.sites().getFirst().status()).isEqualTo("SUCCESS");
        verify(analytics).record(any(), any(), anyLong());
    }

    @Test
    void internalCollectionPagesDoNotRecordSeparateSearchEvents() {
        resolveRegions();
        when(jobKorea.searchPage(criteria, List.of(), 0))
                .thenReturn(new SiteSearchResult("JOBKOREA", "잡코리아", List.of(), 0, null, ""));
        service.searchPageForCollection(new JobSearchPageRequest(criteria, 0, null));
        verifyNoInteractions(analytics);
    }

    @Test
    void rejectsNegativePagesBeforeContactingUpstream() {
        assertThatThrownBy(() -> service.searchPage(new JobSearchPageRequest(criteria, -1, 0)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jobKorea, saramin);
    }
}
