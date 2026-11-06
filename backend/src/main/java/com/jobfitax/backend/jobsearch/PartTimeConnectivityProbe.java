package com.jobfitax.backend.jobsearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Opt-in, bounded connectivity diagnostic; no endpoint and no analytics writes. */
@Component
@ConditionalOnProperty(name = "allez.part.time.probe", havingValue = "true")
class PartTimeConnectivityProbe implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PartTimeConnectivityProbe.class);
    @Override
    public void run(ApplicationArguments args) {
        Thread.startVirtualThread(() -> {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            for (String site : List.of("ALBAMON", "ALBAHEAVEN")) {
                for (int page = 1; page <= 2; page++) {
                    String url = site.equals("ALBAMON")
                            ? "https://www.albamon.com/total-search?keyword=%EC%B9%B4%ED%8E%98&areas=I000&page=" + page
                            : "https://www.alba.co.kr/search/Search?WsSrchWord=%EC%B9%B4%ED%8E%98&Section=0&hidArea=02%7C%7C%EC%A0%84%EC%B2%B4%2C&page=" + page + "&pagesize=20&hidSortCnt=20&hidSort=RANK";
                    try {
                        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                                .header("User-Agent", "AllEZ-Connectivity-Check/1.0")
                                .GET().build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        Document doc = Jsoup.parse(response.body());
                        String selector = site.equals("ALBAMON")
                                ? "a[href*=/jobs/detail/]" : "#jobNormal > li[data-imid]";
                        var rows = doc.select(selector);
                        Element first = rows.first();
                        String firstId = first == null ? "" : site.equals("ALBAMON")
                                ? first.attr("href").replaceAll(".*?/jobs/detail/([0-9]+).*", "$1")
                                : first.attr("data-imid");
                        log.info("PART_TIME_PROBE site={} page={} status={} chars={} rows={} firstId={} htmlTitle={}",
                                site, page, response.statusCode(), response.body().length(), rows.size(),
                                firstId, doc.title().replaceAll("[\\r\\n]", " ").substring(0, Math.min(100, doc.title().length())));
                    } catch (Exception e) {
                        log.info("PART_TIME_PROBE site={} page={} failure={}", site, page, e.getClass().getSimpleName());
                    }
                }
            }
            log.info("PART_TIME_PROBE completed");
        });
    }
}
