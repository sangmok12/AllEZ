package com.jobfitax.backend.jobsearch;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-search")
public class JobSearchController {

    private final JobSearchService searchService;
    private final JobSearchRegionService regionService;

    public JobSearchController(JobSearchService searchService, JobSearchRegionService regionService) {
        this.searchService = searchService;
        this.regionService = regionService;
    }

    @GetMapping("/regions")
    public List<JobSearchRegion> regions() {
        return regionService.findAll();
    }

    @PostMapping("/pages")
    public JobSearchResponse searchPage(@RequestBody JobSearchPageRequest request) {
        return searchService.searchPage(request);
    }

    @PostMapping
    public JobSearchResponse search(@RequestBody JobSearchRequest request) {
        return searchService.search(request);
    }
}
