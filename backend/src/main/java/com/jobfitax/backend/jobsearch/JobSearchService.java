package com.jobfitax.backend.jobsearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.jobfitax.backend.jobsearch.JobSearchRegionService.RegionCodes;
import com.jobfitax.backend.jobsearch.JobSearchResponse.SiteResult;

@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

    private final JobSearchRegionService regionService;
    private final JobKoreaSearchClient jobKoreaClient;
    private final SaraminSearchClient saraminClient;

    public JobSearchService(
            JobSearchRegionService regionService,
            JobKoreaSearchClient jobKoreaClient,
            SaraminSearchClient saraminClient
    ) {
        this.regionService = regionService;
        this.jobKoreaClient = jobKoreaClient;
        this.saraminClient = saraminClient;
    }

    public JobSearchResponse search(JobSearchRequest request) {
        return searchPage(new JobSearchPageRequest(request, 0, 0));
    }

    public JobSearchResponse searchPage(JobSearchPageRequest pageRequest) {
        if (pageRequest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색 조건을 입력해 주세요.");
        }
        JobSearchRequest request = pageRequest.criteria();
        validate(request);
        validatePage(pageRequest.jobKoreaPage());
        validatePage(pageRequest.saraminPage());
        RegionCodes regions = regionService.resolve(request.regionIds());

        List<CompletableFuture<SiteSearchResult>> searches = new ArrayList<>();
        if (pageRequest.jobKoreaPage() != null) {
            int page = pageRequest.jobKoreaPage();
            searches.add(CompletableFuture.supplyAsync(() -> searchSafely("JOBKOREA", "잡코리아", page,
                    () -> jobKoreaClient.searchPage(request, regions.jobKoreaCodes(), page))));
        }
        if (pageRequest.saraminPage() != null) {
            int page = pageRequest.saraminPage();
            searches.add(CompletableFuture.supplyAsync(() -> searchSafely("SARAMIN", "사람인", page,
                    () -> saraminClient.searchPage(request, regions.saraminCodes(), page))));
        }
        List<SiteSearchResult> siteResults = searches.stream().map(CompletableFuture::join).toList();
        List<JobSearchItem> items = siteResults.stream()
                .flatMap(result -> result.items().stream())
                .sorted(Comparator.comparing(JobSearchItem::sortDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<SiteResult> sites = siteResults.stream().map(result -> new SiteResult(
                result.source(), result.sourceLabel(),
                StringUtils.hasText(result.errorMessage()) ? "FAILED" : "SUCCESS",
                result.items().size(), result.totalAvailable(), result.nextPage(), result.errorMessage()
        )).toList();
        // Source failures are returned independently, keeping that source's cursor
        // unchanged so a retry never skips an unsuccessful page.
        return new JobSearchResponse(items, items.size(), sites);
    }

    private SiteSearchResult searchSafely(
            String source, String label, int page,
            java.util.function.Supplier<SiteSearchResult> search
    ) {
        try {
            return search.get();
        } catch (RuntimeException exception) {
            log.warn("{} 통합검색 실패: {}", label, exception.getMessage());
            return SiteSearchResult.failed(source, label, page, label + " 검색 결과를 가져오지 못했습니다. 다시 시도해 주세요.");
        }
    }

    private void validatePage(Integer page) {
        if (page != null && page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 번호가 올바르지 않습니다.");
        }
    }

    private void validate(JobSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.keyword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        if (request.keyword().trim().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 100자 이하로 입력해 주세요.");
        }
        Integer minimum = request.minimumCareerYears();
        Integer maximum = request.maximumCareerYears();
        if ((minimum != null && (minimum < 0 || minimum > 30))
                || (maximum != null && (maximum < 0 || maximum > 30))
                || (minimum != null && maximum != null && minimum > maximum)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "경력 범위를 올바르게 선택해 주세요.");
        }
    }
}
