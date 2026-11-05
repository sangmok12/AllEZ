package com.jobfitax.backend.admin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminReportService {
    private final URI endpoint;
    private final String key;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public AdminReportService(@Value("${SUPABASE_URL:}") String url,
                              @Value("${SUPABASE_SECRET_KEY:}") String key, ObjectMapper mapper) {
        URI target = null;
        try {
            URI base = URI.create(url.strip());
            if ("https".equals(base.getScheme()) && base.getHost() != null && base.getHost().endsWith(".supabase.co")
                    && base.getUserInfo() == null && base.getQuery() == null
                    && (base.getPath().isEmpty() || "/".equals(base.getPath()))) {
                target = base.resolve("/rest/v1/rpc/admin_usage_report");
            }
        } catch (IllegalArgumentException ignored) { }
        this.endpoint = target;
        this.key = key.strip();
        this.mapper = mapper;
    }

    public String report(LocalDate start, LocalDate end, String category, int page) {
        validate(start, end, category, page);
        if (endpoint == null || key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "통계 DB 연결을 확인해 주세요.");
        try {
            var payload = Map.of("p_start", start.toString(), "p_end", end.toString(), "p_category", category, "p_page", page);
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                    .header("apikey", key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "통계를 불러오지 못했습니다. 다시 시도해 주세요.");
            }
            return response.body();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "조회가 중단됐습니다.");
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception ignored) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "통계 서버 응답이 늦어지고 있습니다. 다시 시도해 주세요.");
        }
    }

    static void validate(LocalDate start, LocalDate end, String category, int page) {
        if (start == null || end == null || end.isBefore(start) || Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() > 365
                || !Set.of("all","jobs","part_time","homes").contains(category) || page < 1 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간은 최대 366일입니다. 조건을 확인해 주세요.");
        }
    }
}
