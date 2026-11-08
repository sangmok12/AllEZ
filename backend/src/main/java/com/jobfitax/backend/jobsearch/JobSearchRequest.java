package com.jobfitax.backend.jobsearch;

import java.util.List;

public record JobSearchRequest(
        String keyword,
        List<String> regionIds,
        String careerType,
        Integer minimumCareerYears,
        Integer maximumCareerYears,
        String minimumEducation,
        List<String> employmentTypes,
        String neighborhoodId,
        String neighborhoodName,
        List<Location> locations
) {
    public record Location(String regionId, String neighborhoodId, String neighborhoodName) {}
    public JobSearchRequest(String keyword, List<String> regionIds, String careerType,
                            Integer minimumCareerYears, Integer maximumCareerYears,
                            String minimumEducation, List<String> employmentTypes, String neighborhoodId, String neighborhoodName) {
        this(keyword, regionIds, careerType, minimumCareerYears, maximumCareerYears,
                minimumEducation, employmentTypes, neighborhoodId, neighborhoodName, null);
    }
    public JobSearchRequest(String keyword, List<String> regionIds, String careerType,
                            Integer minimumCareerYears, Integer maximumCareerYears,
                            String minimumEducation, List<String> employmentTypes) {
        this(keyword, regionIds, careerType, minimumCareerYears, maximumCareerYears,
                minimumEducation, employmentTypes, null, null);
    }
    public JobSearchRequest(String keyword, List<String> regionIds, String careerType,
                            Integer minimumCareerYears, Integer maximumCareerYears,
                            String minimumEducation, List<String> employmentTypes, String neighborhoodId) {
        this(keyword, regionIds, careerType, minimumCareerYears, maximumCareerYears,
                minimumEducation, employmentTypes, neighborhoodId, null);
    }
}
