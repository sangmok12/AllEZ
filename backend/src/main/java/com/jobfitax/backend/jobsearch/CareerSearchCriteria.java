package com.jobfitax.backend.jobsearch;

final class CareerSearchCriteria {

    private CareerSearchCriteria() {
    }

    static String effectiveType(JobSearchRequest request) {
        if (request.minimumCareerYears() != null || request.maximumCareerYears() != null) {
            return "EXPERIENCED";
        }
        return request.careerType() == null ? "ANY" : request.careerType();
    }
}
