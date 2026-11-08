package com.jobfitax.backend.jobsearch;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class PartTimeLocations {
    static List<JobSearchRequest> normalize(JobSearchRequest criteria) {
        var selected = criteria.locations();
        if (selected == null || selected.isEmpty()) {
            if (criteria.regionIds() == null || criteria.regionIds().size() <= 1) return List.of(criteria);
            if (criteria.neighborhoodId() != null && !criteria.neighborhoodId().isBlank())
                throw bad("읍·면·동마다 해당 시·군·구를 선택해 주세요.");
            selected = criteria.regionIds().stream().map(id -> new JobSearchRequest.Location(id, null, null)).toList();
        }
        if (selected.size() > 5) throw bad("지역은 최대 5곳까지 선택할 수 있습니다.");
        List<JobSearchRequest.Location> normalized = new ArrayList<>();
        for (var area : selected) {
            if (area == null || area.regionId() == null || !area.regionId().matches("[A-Z0-9]{4}(?:_[A-Z0-9]{4})?"))
                throw bad("지역을 다시 선택해 주세요.");
            String dong = area.neighborhoodId();
            if (dong != null && !dong.isBlank() && (!dong.matches("[A-Z0-9]{8}")
                    || !dong.startsWith(area.regionId().substring(area.regionId().lastIndexOf('_') + 1))))
                throw bad("읍·면·동의 시·군·구를 다시 선택해 주세요.");
            if (normalized.stream().anyMatch(existing -> covers(existing, area))) continue;
            normalized.removeIf(existing -> covers(area, existing));
            normalized.add(area);
        }
        return normalized.stream().map(area -> new JobSearchRequest(criteria.keyword(), List.of(area.regionId()),
                criteria.careerType(), criteria.minimumCareerYears(), criteria.maximumCareerYears(),
                criteria.minimumEducation(), criteria.employmentTypes(), area.neighborhoodId(), area.neighborhoodName())).toList();
    }

    private static boolean covers(JobSearchRequest.Location a, JobSearchRequest.Location b) {
        if (a.neighborhoodId() != null && !a.neighborhoodId().isBlank())
            return a.regionId().equals(b.regionId()) && a.neighborhoodId().equals(b.neighborhoodId());
        return a.regionId().equals(b.regionId()) || b.regionId().startsWith(a.regionId() + "_");
    }

    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
