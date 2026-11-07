package com.jobfitax.backend.jobsearch;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/part-time-search")
class PartTimeSearchController {
    private final PartTimeSearchService service;
    PartTimeSearchController(PartTimeSearchService service) { this.service = service; }
    @PostMapping("/pages")
    PartTimeSearchService.Response pages(@RequestBody PartTimeSearchService.Request request) { return service.search(request); }
    @GetMapping("/neighborhoods")
    List<PartTimeNeighborhoods.Neighborhood> neighborhoods(@RequestParam String regionId) {
        return service.neighborhoods(regionId);
    }
}

@Service
class PartTimeSearchService {
    private static final Map<String,String> ALBA_CODES = Map.ofEntries(
        Map.entry("I000","02"), Map.entry("B000","031"), Map.entry("F000","053"),
        Map.entry("G000","042"), Map.entry("H000","051"), Map.entry("J000","052"),
        Map.entry("K000","032"), Map.entry("A000","033"), Map.entry("C000","055"),
        Map.entry("D000","054"), Map.entry("L000","062"), Map.entry("M000","063"),
        Map.entry("P000","043"), Map.entry("O000","041"), Map.entry("N000","064"), Map.entry("1000","044"));
    private final JobSearchRegionService regions;
    private final PartTimeNeighborhoods neighborhoods;
    private final java.util.function.Function<String, String> pageLoader;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    @org.springframework.beans.factory.annotation.Autowired
    PartTimeSearchService(JobSearchRegionService regions) { this(regions, new PartTimeNeighborhoods()); }
    PartTimeSearchService(JobSearchRegionService regions, PartTimeNeighborhoods neighborhoods) {
        this(regions, neighborhoods, null);
    }
    PartTimeSearchService(JobSearchRegionService regions, PartTimeNeighborhoods neighborhoods,
                          java.util.function.Function<String, String> pageLoader) {
        this.regions = regions;
        this.neighborhoods = neighborhoods;
        this.pageLoader = pageLoader;
    }
    record Request(JobSearchRequest criteria, Integer albamonPage, Integer albaheavenPage) {}
    record Item(String source, String sourceLabel, String postingId, String companyName, String title,
                List<String> locations, String career, String education, List<String> employmentTypes,
                List<String> skills, String registeredDate, String deadline, String url, String payText) {}
    record Response(List<Item> items, int totalReturned, List<JobSearchResponse.SiteResult> sites) {}
    record Region(String monCode, String albaCode, String district) {}
    List<PartTimeNeighborhoods.Neighborhood> neighborhoods(String regionId) {
        Region region = resolve(List.of(regionId));
        if (region.district().equals("전체") && !regionId.equals("1000"))
            throw bad("읍·면·동을 선택하려면 시·군·구를 먼저 선택해 주세요.");
        return neighborhoods.list(region.monCode());
    }
    Region resolve(List<String> ids) {
        if (ids == null || ids.isEmpty()) return new Region("", "", "");
        if (ids.size() != 1) throw bad("알바 검색은 지역을 하나만 선택해 주세요.");
        String id = ids.getFirst();
        for (var province : regions.findAll()) {
            if (province.id().equals(id)) return new Region(province.id(), ALBA_CODES.get(province.id()), "전체");
            for (var area : province.areas()) {
                if (area.id().equals(id) && area.jobKoreaCodes().size() == 1) {
                    return new Region(area.jobKoreaCodes().getFirst(), ALBA_CODES.get(province.id()), area.name());
                }
            }
        }
        throw bad("지역을 다시 선택해 주세요.");
    }
    Response search(Request request) {
        validateCriteria(request == null ? null : request.criteria());
        if (request.albamonPage() == null && request.albaheavenPage() == null) throw bad("조회할 페이지가 없습니다.");
        for (Integer page : Arrays.asList(request.albamonPage(), request.albaheavenPage()))
            if (page != null && (page < 1 || page > 100000)) throw bad("페이지 번호가 올바르지 않습니다.");
        Region region = resolve(request.criteria().regionIds());
        String keyword = request.criteria().keyword() == null ? "" : request.criteria().keyword().strip();
        String neighborhoodId = request.criteria().neighborhoodId();
        PartTimeNeighborhoods.Neighborhood neighborhood = null;
        if (neighborhoodId != null && !neighborhoodId.isBlank()) {
            if (!neighborhoodId.matches("[A-Z0-9]{8}")) throw bad("읍·면·동을 다시 선택해 주세요.");
            if (request.criteria().regionIds() == null || request.criteria().regionIds().size() != 1)
                throw bad("읍·면·동의 시·군·구를 선택해 주세요.");
            neighborhood = neighborhoods(request.criteria().regionIds().getFirst()).stream()
                .filter(item -> item.id().equals(neighborhoodId)).findFirst()
                .orElseThrow(() -> bad("선택한 시·군·구에 속한 읍·면·동을 다시 선택해 주세요."));
        }
        final var selectedNeighborhood = neighborhood;
        List<CompletableFuture<Response>> tasks = new ArrayList<>();
        if (request.albamonPage() != null) tasks.add(CompletableFuture.supplyAsync(
            () -> safely("ALBAMON", keyword, region, selectedNeighborhood, request.albamonPage())));
        if (request.albaheavenPage() != null) tasks.add(CompletableFuture.supplyAsync(
            () -> safely("ALBAHEAVEN", keyword, region, selectedNeighborhood, request.albaheavenPage())));
        List<Response> responses = tasks.stream().map(CompletableFuture::join).toList();
        // Interleave sources; source-specific advertised placement is not a common ranking.
        List<Item> items = new ArrayList<>();
        int max = responses.stream().mapToInt(r -> r.items().size()).max().orElse(0);
        for (int i=0; i<max; i++) for (Response r : responses) if (i<r.items().size()) items.add(r.items().get(i));
        return new Response(items, items.size(), responses.stream().flatMap(r -> r.sites().stream()).toList());
    }
    static void validateCriteria(JobSearchRequest criteria) {
        if (criteria == null) throw bad("검색어 또는 지역을 선택해 주세요.");
        String keyword = criteria.keyword() == null ? "" : criteria.keyword().strip();
        if (keyword.length() > 20) throw bad("알바 검색어는 20자 이하로 입력해 주세요.");
        if (keyword.isBlank() && (criteria.regionIds() == null || criteria.regionIds().isEmpty()))
            throw bad("검색어 또는 지역을 선택해 주세요.");
    }
    static String sourceUrl(String site, String keyword, Region region,
                            PartTimeNeighborhoods.Neighborhood neighborhood, int page) {
        String monCode = neighborhood == null ? region.monCode() : neighborhood.id();
        if (site.equals("ALBAMON")) return (keyword.isBlank() ? "https://www.albamon.com/jobs/area?page=" + page
                : "https://www.albamon.com/total-search?keyword=" + enc(keyword) + "&page=" + page)
                + (monCode.isEmpty() ? "" : "&areas=" + enc(monCode));
        if (usesAlbaArea(keyword, region, neighborhood)) {
            String dong = neighborhood == null ? "전체" : neighborhood.name();
            // Native form captured from a real 구리시 인창동 search. Location is not a keyword.
            return "https://www.alba.co.kr/job/area/mainlocal?schnm=LOCAL&viewtype=L"
                + "&sidocd=" + enc(region.albaCode())
                + "&hidListView=LIST&hidSortCnt=50&hidSortFilter=Y&hidSearchyn=Y"
                + "&strAreaMulti=" + enc(region.albaCode() + "||" + region.district() + "||" + dong)
                + "&selGugun=" + enc(region.district()) + "&selDong=" + enc(dong)
                + "&schIncludeText=" + enc(keyword) + "&page=" + page;
        }
        return "https://www.alba.co.kr/search/Search?Section=0&WsSrchWord=" + enc(keyword)
            + "&page=" + page + "&pagesize=20&hidSortCnt=20&hidSort=RANK"
            + (region.albaCode().isEmpty() ? "" : "&hidArea=" + enc(region.albaCode()+"||"+region.district()+","));
    }
    private static boolean usesAlbaArea(String keyword, Region region, PartTimeNeighborhoods.Neighborhood neighborhood) {
        return neighborhood != null || (keyword.isBlank() && !region.albaCode().isEmpty());
    }
    private Response safely(String site, String keyword, Region region,
                            PartTimeNeighborhoods.Neighborhood neighborhood, int page) {
        try {
            String url = sourceUrl(site, keyword, region, neighborhood, page);
            String html;
            if (pageLoader != null) html = pageLoader.apply(url);
            else {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(35)).header("User-Agent","AllEZ-Search/1.0").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("source unavailable");
                html = response.body();
            }
            if (site.equals("ALBAMON") && keyword.isBlank()) return parseMonArea(html, page);
            if (site.equals("ALBAHEAVEN") && usesAlbaArea(keyword, region, neighborhood))
                return parseAlbaArea(html, page, region, neighborhood);
            return parse(site, html, page);
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            String label = site.equals("ALBAMON") ? "알바몬" : "알바천국";
            return new Response(List.of(),0,List.of(new JobSearchResponse.SiteResult(
                site,label,"FAILED",0,0,page,label+" 검색 결과를 불러오지 못했습니다.")));
        }
    }
    static Response parseAlbaArea(String html, int page, Region region, PartTimeNeighborhoods.Neighborhood neighborhood) {
        Document doc = Jsoup.parse(html);
        String dong = neighborhood == null ? "전체" : neighborhood.name();
        Element selected = doc.selectFirst("input[name=strAreaMulti]");
        String expected = region.albaCode() + "||" + region.district() + "||" + dong;
        // Reject a redirected/unfiltered page rather than presenting wider-region results.
        if (selected == null || !selected.val().equals(expected))
            throw new IllegalStateException("source did not preserve selected area");
        return parse("ALBAHEAVEN", html, page, 50);
    }
    static Response parseMonArea(String html, int page) throws Exception {
        Element script = Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
        if (script == null) throw new IllegalStateException("unrecognized area page");
        var data = new tools.jackson.databind.ObjectMapper().readTree(script.data());
        for (var query : data.path("props").path("pageProps").path("dehydratedState").path("queries")) {
            var base = query.path("state").path("data").path("base");
            var collection = base.path("normal").path("collection");
            if (!collection.isArray() || !base.path("pagination").path("totalCount").isIntegralNumber()) continue;
            int total = base.path("pagination").path("totalCount").asInt();
            int size = base.path("pagination").path("size").asInt(20);
            if (size < 1 || total < 0) throw new IllegalStateException("invalid pagination");
            Map<String, Item> items = new LinkedHashMap<>();
            for (var row : collection) {
                String id = row.path("recruitNo").asText(), title = row.path("recruitTitle").asText();
                if (!id.matches("[0-9]+") || title.isBlank()) throw new IllegalStateException("invalid posting");
                String location = row.path("workplaceArea").asText();
                items.putIfAbsent(id, new Item("ALBAMON", "알바몬", id, row.path("companyName").asText(), title,
                    location.isBlank() ? List.of() : List.of(location), "", "", List.of(), List.of(),
                    row.path("postedDate").asText(), row.path("closingDate").asText(),
                    "https://www.albamon.com/jobs/detail/" + id,
                    (row.path("payType").path("description").asText() + " " + row.path("pay").asText()).strip()));
            }
            if (total > 0 && items.isEmpty()) throw new IllegalStateException("missing area results");
            return new Response(List.copyOf(items.values()), items.size(), List.of(new JobSearchResponse.SiteResult(
                "ALBAMON", "알바몬", "SUCCESS", items.size(), total, total > (long) page * size ? page + 1 : null, "")));
        }
        throw new IllegalStateException("unrecognized area results");
    }
    static Response parse(String site, String html, int page) {
        return parse(site, html, page, 20);
    }
    private static Response parse(String site, String html, int page, int pageSize) {
        Document doc = Jsoup.parse(html);
        boolean mon = site.equals("ALBAMON");
        String label = mon ? "알바몬" : "알바천국";
        Element normal = doc.selectFirst("#NormalInfo");
        Element search = doc.selectFirst("#SearchJob");
        String countText = mon ? doc.title() : normal != null ? normal.text()
            : search == null ? "" : search.parent().text();
        var count = Pattern.compile(mon ? "([0-9,]+)건" : "총\\s*([0-9,]+)\\s*건").matcher(countText);
        if (!count.find()) throw new IllegalStateException("unrecognized result page");
        int total = Integer.parseInt(count.group(1).replace(",",""));
        Map<String,Item> items = new LinkedHashMap<>();
        if (mon) {
            for (Element link : doc.select("a[href*=/jobs/detail/]")) {
                Element article = link.closest(".list-item-recruit__contents--article");
                if (article == null) continue;
                var idMatch = Pattern.compile("/jobs/detail/([0-9]+)").matcher(link.attr("href"));
                if (!idMatch.find()) continue;
                String id = idMatch.group(1);
                Element titleNode = link.selectFirst(".list-item-recruit__recruit-title");
                if (titleNode == null) continue;
                Element company = titleNode.selectFirst(".list-item-recruit__grey-text");
                Element titleCopy = titleNode.clone();
                titleCopy.select(".list-item-recruit__grey-text, .list-item-recruit__bar, i").remove();
                String title = titleCopy.text();
                if (title.isBlank()) continue;
                Element row = article.closest("li");
                List<String> locations = row == null ? List.of() : row.select("[class*=location], [class*=area]")
                    .stream().map(Element::text).filter(t -> !t.isBlank()).distinct().toList();
                String metadata = article.text();
                String pay = match("(시급|일급|주급|월급|연봉|건별)\\s*([0-9,]+원)",metadata);
                String registered = match("등록일\\s+(.+?)(?=\\s*마감일|$)", metadata).replaceFirst("^등록일\\s*","");
                String deadline = match("마감일\\s+(.+)$",metadata).replaceFirst("^마감일\\s*","");
                items.putIfAbsent(id,new Item(site,label,id,company == null ? "" : company.text(),title,
                    locations,"","",List.of(),List.of(),registered,deadline,
                    "https://www.albamon.com/jobs/detail/"+id,pay));
            }
        } else {
            // Native area pages use the normal-information table; total-search also has a list layout.
            for (Element row : doc.select("#NormalInfo table tbody > tr")) {
                Element link = row.selectFirst("td.title a[href*='adid=']");
                if (link == null) continue; // The separate summary/preview rows are not postings.
                var idMatch = Pattern.compile("[?&]adid=([0-9]+)(?:&|$)", Pattern.CASE_INSENSITIVE).matcher(link.attr("href"));
                if (!idMatch.find()) throw new IllegalStateException("invalid area posting link");
                String id = idMatch.group(1), title = link.select("span.title").text();
                if (title.isBlank()) throw new IllegalStateException("invalid area posting title");
                Element location = row.selectFirst("td.local");
                Element cleanLocation = location == null ? null : location.clone();
                if (cleanLocation != null) cleanLocation.select("div, script").remove();
                String pay = row.select("td.pay .number").text();
                if (pay.matches("[0-9,]+")) pay += "원";
                items.putIfAbsent(id, new Item(site, label, id, link.select("span.company").text(), title,
                    cleanLocation == null || cleanLocation.text().isBlank() ? List.of() : List.of(cleanLocation.text()),
                    "", "", List.of(), List.of(), row.select("td.regDate").text(), "",
                    "https://www.alba.co.kr/job/Detail?adid=" + id,
                    (row.select("td.pay .payIcon").text() + " " + pay).strip()));
            }
            for (Element row : doc.select("#jobNormal > li[data-imid]")) {
                String id = row.attr("data-imid");
                if (!id.matches("[0-9]+")) continue;
                Element company = row.selectFirst(".job-list__company");
                Element copy = company == null ? null : company.clone();
                if (copy != null) copy.select(".job-list__area").remove();
                String title = row.select(".job-list__subject").text();
                if (title.isBlank()) continue;
                Element date = row.selectFirst(".regDate");
                Element dateCopy = date == null ? null : date.clone();
                if (dateCopy != null) dateCopy.select("a").remove();
                items.putIfAbsent(id,new Item(site,label,id,copy == null ? "" : copy.text(),title,
                    row.select(".job-list__area").eachText(),"","",List.of(),List.of(),
                    dateCopy == null ? "" : dateCopy.text(),row.select(".job-list__deadline").text(),
                    "https://www.alba.co.kr/job/Detail?adid="+id,
                    (row.select(".payIcon").text()+" "+row.select(".job-list__number").text()+"원").strip()));
            }
        }
        if (total > 0 && items.isEmpty()) throw new IllegalStateException("result format changed");
        Integer next = total > (long) page * pageSize && !items.isEmpty() ? page+1 : null;
        return new Response(List.copyOf(items.values()),items.size(),List.of(
            new JobSearchResponse.SiteResult(site,label,"SUCCESS",items.size(),total,next,"")));
    }
    private static String enc(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private static String match(String regex, String value) {
        var m = Pattern.compile(regex).matcher(value); return m.find() ? m.group() : "";
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
