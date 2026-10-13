package com.jobfitax.backend.jobsearch;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

@Component
class JobKoreaSearchClient {

    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 5;
    private static final int MAX_RESULTS = 250;
    private static final String ENDPOINT = "https://www.jobkorea.co.kr/Search/api/display/v2/jobs";

    private final RestClient restClient;
    private final JobSearchRegionService regionService;

    JobKoreaSearchClient(JobSearchRegionService regionService) {
        this.regionService = regionService;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    SiteSearchResult search(JobSearchRequest request, List<String> locationCodes) {
        Map<String, JobSearchItem> unique = new LinkedHashMap<>();
        int totalAvailable = 0;

        for (int page = 0; page < MAX_PAGES && unique.size() < MAX_RESULTS; page++) {
            JsonNode response = restClient.post()
                    .uri(ENDPOINT)
                    .header(HttpHeaders.USER_AGENT, userAgent())
                    .header(HttpHeaders.REFERER, "https://www.jobkorea.co.kr/Search/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildPayload(request, locationCodes, page))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) break;
            totalAvailable = response.path("totalElements").asInt();
            JsonNode content = response.path("content");
            if (!content.isArray() || content.isEmpty()) break;

            for (JsonNode item : content) {
                JobSearchItem converted = convert(item);
                if (StringUtils.hasText(converted.postingId())) unique.putIfAbsent(converted.postingId(), converted);
                if (unique.size() >= MAX_RESULTS) break;
            }
            if (content.size() < PAGE_SIZE) break;
        }

        List<JobSearchItem> items = List.copyOf(unique.values());
        return new SiteSearchResult("JOBKOREA", "잡코리아", items, totalAvailable,
                totalAvailable > items.size(), "");
    }

    private Map<String, Object> buildPayload(JobSearchRequest request, List<String> locations, int page) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pageSize", PAGE_SIZE);
        payload.put("page", page);
        payload.put("sortProperty", "2");
        payload.put("sortDirection", "DESC");
        payload.put("keyword", request.keyword().trim());
        payload.put("deviceType", "PC");
        payload.put("benefitCodeList", List.of());
        payload.put("careerList", careerCodes(CareerSearchCriteria.effectiveType(request)));
        payload.put("careerMin", value(request.minimumCareerYears()));
        payload.put("careerMax", value(request.maximumCareerYears()));
        payload.put("companyTypeList", List.of());
        payload.put("designationCodeList", List.of());
        payload.put("educationCodeList", educationCodes(request.minimumEducation()));
        payload.put("employmentTypeList", employmentCodes(request.employmentTypes()));
        payload.put("excludeKeywordList", List.of());
        payload.put("filterList", List.of());
        payload.put("industryCodeList", List.of());
        payload.put("locationList", locations);
        payload.put("payMax", "");
        payload.put("payMin", "");
        payload.put("payType", "1");
        payload.put("period", "");
        return payload;
    }

    private JobSearchItem convert(JsonNode item) {
        String id = item.path("id").asText();
        String careerType = careerLabel(
                item.path("careerType").asText(),
                text(item.path("careerRange"))
        );
        String education = switch (item.path("educationCode").asText()) {
            case "0" -> "학력무관";
            case "1" -> "고졸";
            case "2" -> "대졸(2,3년)";
            case "3" -> "대졸(4년)";
            case "4" -> "석사";
            case "5" -> "박사";
            default -> "";
        };
        OffsetDateTime sortDate = parseDate(item.path("createdAt").asText());
        String registered = sortDate == null ? "" : sortDate.toLocalDate().toString();
        String deadline = text(item.path("applicationPeriod").path("end"));
        if (deadline.length() >= 10) deadline = deadline.substring(0, 10);

        return new JobSearchItem(
                "JOBKOREA", "잡코리아", id,
                firstText(item, "companyName", "postingCompanyName"),
                text(item.path("title")),
                regionService.jobKoreaNames(item.path("areaCodeList")),
                careerType,
                education,
                mapEmployment(item.path("employmentTypeCodeList")),
                skills(item),
                registered,
                deadline,
                "https://www.jobkorea.co.kr/Recruit/GI_Read/" + id,
                sortDate
        );
    }

    private List<String> skills(JsonNode item) {
        String value = firstText(item, "_internal_featureToolCode", "_internal_featureWorkCode", "jobClassificationOrIndustry");
        if (!StringUtils.hasText(value)) return List.of();
        return List.of(value.replaceAll("^,+|,+$", "").split(","));
    }

    private List<String> mapEmployment(JsonNode node) {
        Map<String, String> names = Map.of(
                "1/0", "정규직", "2/0", "계약직", "3/0", "인턴", "4/0", "파견직",
                "5/0", "도급", "6/0", "프리랜서", "7/0", "아르바이트", "8/0", "연수생·교육생"
        );
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) values.add(names.getOrDefault(value.asText(), value.asText()));
        return List.copyOf(values);
    }

    private List<Integer> careerCodes(String careerType) {
        if ("NEWCOMER".equals(careerType)) return List.of(1);
        if ("EXPERIENCED".equals(careerType)) return List.of(2);
        if ("NO_PREFERENCE".equals(careerType)) return List.of(4);
        return List.of();
    }

    static String careerLabel(String careerType, String careerRange) {
        return switch (careerType) {
            case "1" -> "신입";
            case "2" -> !StringUtils.hasText(careerRange) || "100".equals(careerRange)
                    ? "경력" : "경력 " + careerRange + "년";
            case "3" -> "신입·경력";
            case "4" -> "경력무관";
            default -> "";
        };
    }

    private List<Integer> educationCodes(String education) {
        return switch (education == null ? "ANY" : education) {
            case "NO_PREFERENCE" -> List.of(0);
            case "HIGH_SCHOOL" -> List.of(1, 2, 3, 4, 5);
            case "COLLEGE" -> List.of(2, 3, 4, 5);
            case "UNIVERSITY" -> List.of(3, 4, 5);
            case "MASTER" -> List.of(4, 5);
            case "DOCTOR" -> List.of(5);
            default -> List.of();
        };
    }

    private List<String> employmentCodes(List<String> types) {
        if (types == null) return List.of();
        Map<String, String> codes = Map.of(
                "PERMANENT", "1/0", "CONTRACT", "2/0", "INTERN", "3/0",
                "DISPATCH", "4/0", "FREELANCE", "6/0", "PART_TIME", "7/0"
        );
        return types.stream().map(codes::get).filter(StringUtils::hasText).toList();
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) values.add(value.asText());
        return List.copyOf(values);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node.path(field));
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? "" : node.asText();
    }

    private String value(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    private OffsetDateTime parseDate(String value) {
        try {
            return StringUtils.hasText(value) ? OffsetDateTime.parse(value) : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String userAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124 Safari/537.36";
    }
}
