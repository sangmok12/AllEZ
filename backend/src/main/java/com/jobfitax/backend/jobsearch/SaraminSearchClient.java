package com.jobfitax.backend.jobsearch;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class SaraminSearchClient {

    private static final int PAGE_SIZE = 50;
    private static final String ENDPOINT = "https://www.saramin.co.kr/zf_user/search/get-recruit-list";
    private static final String BASE_URL = "https://www.saramin.co.kr";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    SaraminSearchClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(40));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    SiteSearchResult searchPage(JobSearchRequest request, List<String> locationCodes, int page) {
        String responseBody = restClient.get()
                .uri(buildUri(request, locationCodes, page + 1))
                .header(HttpHeaders.USER_AGENT, userAgent())
                .header(HttpHeaders.REFERER, "https://www.saramin.co.kr/zf_user/search/recruit")
                .header("X-Requested-With", "XMLHttpRequest")
                .retrieve()
                .body(String.class);
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("사람인 검색 응답이 비어 있습니다.");
        }
        JsonNode response = objectMapper.readTree(responseBody);
        if (!response.has("innerHTML") || !response.has("count")) {
            throw new IllegalStateException("사람인 검색 응답을 확인할 수 없습니다.");
        }
        int totalAvailable = parseNumber(response.path("count").asText());
        List<JobSearchItem> pageItems = parseItems(response.path("innerHTML").asText());
        if (pageItems.isEmpty() && (long) page * PAGE_SIZE < totalAvailable) {
            throw new IllegalStateException("사람인 공고를 읽지 못했습니다. 다시 시도해 주세요.");
        }
        Map<String, JobSearchItem> unique = new LinkedHashMap<>();
        for (JobSearchItem item : pageItems) unique.putIfAbsent(item.postingId(), item);
        Integer nextPage = SearchPagination.nextPage(page, pageItems.size(), totalAvailable, PAGE_SIZE);
        return new SiteSearchResult("SARAMIN", "사람인",
                List.copyOf(unique.values()), totalAvailable, nextPage, "");
    }

    private URI buildUri(JobSearchRequest request, List<String> locations, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("searchType", "search")
                .queryParam("searchword", request.keyword().trim())
                .queryParam("recruitSort", "reg_dt")
                .queryParam("recruitPageCount", PAGE_SIZE)
                .queryParam("recruitPage", page)
                .queryParam("search_optional_item", "y")
                .queryParam("search_done", "y")
                .queryParam("panel_count", "y")
                .queryParam("preview", "y")
                .queryParam("mainSearch", "y");

        if (!locations.isEmpty()) builder.queryParam("loc_cd", String.join(",", locations));
        addCareerParams(builder, request);
        addEducationParams(builder, request.minimumEducation());
        List<String> employment = employmentCodes(request.employmentTypes());
        if (!employment.isEmpty()) builder.queryParam("job_type", String.join(",", employment));
        return builder.build().encode().toUri();
    }

    private void addCareerParams(UriComponentsBuilder builder, JobSearchRequest request) {
        String careerType = CareerSearchCriteria.effectiveType(request);
        switch (careerType) {
            case "NEWCOMER" -> builder.queryParam("exp_cd", "1");
            case "EXPERIENCED" -> builder.queryParam("exp_cd", "2");
            case "NO_PREFERENCE" -> {
                builder.queryParam("exp_none", "y");
                builder.queryParam("exp_cd", "99");
            }
            default -> builder.queryParam("exp_none", "y");
        }
        if (request.minimumCareerYears() != null) builder.queryParam("exp_min", request.minimumCareerYears());
        if (request.maximumCareerYears() != null) builder.queryParam("exp_max", request.maximumCareerYears());
    }

    private void addEducationParams(UriComponentsBuilder builder, String education) {
        switch (education == null ? "ANY" : education) {
            case "NO_PREFERENCE" -> builder.queryParam("edu_none", "y");
            case "HIGH_SCHOOL" -> builder.queryParam("edu_min", "6");
            case "COLLEGE" -> builder.queryParam("edu_min", "7");
            case "UNIVERSITY" -> builder.queryParam("edu_min", "8");
            case "MASTER" -> builder.queryParam("edu_min", "9");
            case "DOCTOR" -> builder.queryParam("edu_min", "5");
            default -> { }
        }
    }

    private List<String> employmentCodes(List<String> types) {
        if (types == null) return List.of();
        Map<String, String> codes = Map.of(
                "PERMANENT", "1", "CONTRACT", "2", "INTERN", "4",
                "DISPATCH", "6", "FREELANCE", "9", "PART_TIME", "5"
        );
        return types.stream().map(codes::get).filter(StringUtils::hasText).toList();
    }

    private List<JobSearchItem> parseItems(String html) {
        if (!StringUtils.hasText(html)) return List.of();
        Document document = Jsoup.parse(html, BASE_URL);
        List<JobSearchItem> items = new ArrayList<>();

        for (Element block : document.select(".item_recruit")) {
            Element titleLink = block.selectFirst("h2.job_tit a");
            if (titleLink == null) continue;
            String id = block.attr("value");
            if (!StringUtils.hasText(id)) {
                String href = titleLink.attr("href");
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("rec_idx=(\\d+)").matcher(href);
                if (matcher.find()) id = matcher.group(1);
            }
            if (!StringUtils.hasText(id)) continue;

            List<String> conditions = block.select(".job_condition > span").eachText();
            String location = conditions.isEmpty() ? "" : conditions.get(0);
            String career = conditions.stream().filter(value -> value.matches(".*(신입|경력).*"))
                    .findFirst().orElse(conditions.size() > 1 ? conditions.get(1) : "");
            String education = conditions.stream().filter(value -> value.matches(".*(학력|고졸|대졸|대학|석사|박사).*"))
                    .findFirst().orElse("");
            List<String> employment = conditions.stream()
                    .filter(value -> value.matches(".*(정규직|계약직|인턴|프리랜서|파견|아르바이트|병역특례).*"))
                    .toList();
            Set<String> skillSet = new LinkedHashSet<>(block.select(".job_sector a").eachText());
            String registered = parseRegistered(block.selectFirst(".job_sector .job_day"));
            String deadline = text(block.selectFirst(".job_date .date"));
            OffsetDateTime sortDate = toSortDate(registered);

            items.add(new JobSearchItem(
                    "SARAMIN", "사람인", id,
                    text(block.selectFirst(".corp_name a")),
                    titleLink.text().trim(),
                    StringUtils.hasText(location) ? List.of(location) : List.of(),
                    career,
                    education,
                    employment,
                    List.copyOf(skillSet),
                    registered,
                    normalizeDeadline(deadline),
                    titleLink.absUrl("href"),
                    sortDate
            ));
        }
        return List.copyOf(items);
    }

    private String parseRegistered(Element element) {
        if (element == null) return "";
        String value = element.text().replace("등록일", "").trim();
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yy/MM/dd"));
            return date.toString();
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private String normalizeDeadline(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith("~")) return value;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})/(\\d{1,2})").matcher(value);
        if (!matcher.find()) return value;
        int month = Integer.parseInt(matcher.group(1));
        int day = Integer.parseInt(matcher.group(2));
        LocalDate now = LocalDate.now();
        int year = now.getMonthValue() >= 11 && month <= 2 ? now.getYear() + 1 : now.getYear();
        return LocalDate.of(year, month, day).toString();
    }

    private OffsetDateTime toSortDate(String value) {
        try {
            return StringUtils.hasText(value)
                    ? LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.ofHours(9)) : null;
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private int parseNumber(String value) {
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String text(Element element) {
        return element == null ? "" : element.text().trim();
    }

    private String userAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124 Safari/537.36";
    }
}
