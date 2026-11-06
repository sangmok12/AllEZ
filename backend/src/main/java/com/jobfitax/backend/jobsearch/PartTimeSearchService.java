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
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    PartTimeSearchService(JobSearchRegionService regions) { this.regions = regions; }
    record Request(JobSearchRequest criteria, Integer albamonPage, Integer albaheavenPage) {}
    record Item(String source, String sourceLabel, String postingId, String companyName, String title,
                List<String> locations, String career, String education, List<String> employmentTypes,
                List<String> skills, String registeredDate, String deadline, String url, String payText) {}
    record Response(List<Item> items, int totalReturned, List<JobSearchResponse.SiteResult> sites) {}
    record Region(String monCode, String albaCode, String district) {}
    Region resolve(List<String> ids) {
        if (ids == null || ids.isEmpty()) return new Region("", "", "");
        if (ids.size() != 1) throw bad("알바 검색은 지역을 하나만 선택해 주세요.");
        String id = ids.getFirst();
        for (var province : regions.findAll()) {
            if (province.id().equals(id)) return new Region(province.id(), ALBA_CODES.get(province.id()), "전체");

        }
        throw bad("알바 검색은 시·도 지역을 선택해 주세요.");
    }
    Response search(Request request) {
        if (request == null || request.criteria() == null || request.criteria().keyword() == null
                || request.criteria().keyword().isBlank() || request.criteria().keyword().strip().length() > 20)
            throw bad("검색어를 1~20자로 입력해 주세요.");
        if (request.albamonPage() == null && request.albaheavenPage() == null) throw bad("조회할 페이지가 없습니다.");
        for (Integer page : Arrays.asList(request.albamonPage(), request.albaheavenPage()))
            if (page != null && (page < 1 || page > 100000)) throw bad("페이지 번호가 올바르지 않습니다.");
        Region region = resolve(request.criteria().regionIds());
        List<CompletableFuture<Response>> tasks = new ArrayList<>();
        if (request.albamonPage() != null) tasks.add(CompletableFuture.supplyAsync(
            () -> safely("ALBAMON", request.criteria().keyword(), region, request.albamonPage())));
        if (request.albaheavenPage() != null) tasks.add(CompletableFuture.supplyAsync(
            () -> safely("ALBAHEAVEN", request.criteria().keyword(), region, request.albaheavenPage())));
        List<Response> responses = tasks.stream().map(CompletableFuture::join).toList();
        // Interleave sources; source-specific advertised placement is not a common ranking.
        List<Item> items = new ArrayList<>();
        int max = responses.stream().mapToInt(r -> r.items().size()).max().orElse(0);
        for (int i=0; i<max; i++) for (Response r : responses) if (i<r.items().size()) items.add(r.items().get(i));
        return new Response(items, items.size(), responses.stream().flatMap(r -> r.sites().stream()).toList());
    }
    private Response safely(String site, String keyword, Region region, int page) {
        try {
            String url = site.equals("ALBAMON")
                ? "https://www.albamon.com/total-search?keyword=" + enc(keyword) + "&page=" + page
                    + (region.monCode().isEmpty() ? "" : "&areas=" + enc(region.monCode()))
                : "https://www.alba.co.kr/search/Search?Section=0&WsSrchWord=" + enc(keyword)
                    + "&page=" + page + "&pagesize=20&hidSortCnt=20&hidSort=RANK"
                    + (region.albaCode().isEmpty() ? "" : "&hidArea=" + enc(region.albaCode()+"||"+region.district()+","));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(35)).header("User-Agent","AllEZ-Search/1.0").GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("source unavailable");
            return parse(site, response.body(), page);
        } catch (Exception ignored) {
            String label = site.equals("ALBAMON") ? "알바몬" : "알바천국";
            return new Response(List.of(),0,List.of(new JobSearchResponse.SiteResult(
                site,label,"FAILED",0,0,page,label+" 검색 결과를 불러오지 못했습니다.")));
        }
    }
    static Response parse(String site, String html, int page) {
        Document doc = Jsoup.parse(html);
        boolean mon = site.equals("ALBAMON");
        String label = mon ? "알바몬" : "알바천국";
        String countText = mon ? doc.title() : doc.select("#SearchJob").first() == null
            ? "" : doc.select("#SearchJob").first().parent().text();
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
        Integer next = total > page * 20 && !items.isEmpty() ? page+1 : null;
        return new Response(List.copyOf(items.values()),items.size(),List.of(
            new JobSearchResponse.SiteResult(site,label,"SUCCESS",items.size(),total,next,"")));
    }
    private static String enc(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    private static String match(String regex, String value) {
        var m = Pattern.compile(regex).matcher(value); return m.find() ? m.group() : "";
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
