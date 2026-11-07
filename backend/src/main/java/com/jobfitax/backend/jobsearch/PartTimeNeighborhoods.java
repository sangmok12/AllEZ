package com.jobfitax.backend.jobsearch;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Native source codes: never derive an 읍면동 code from its name or a postal code. */
final class PartTimeNeighborhoods {
    record Neighborhood(String id, String name) {}
    private record Cached(List<Neighborhood> items, Instant expiresAt) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Function<String, String> loader;

    PartTimeNeighborhoods() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        loader = code -> {
            try {
                var response = client.send(HttpRequest.newBuilder(URI.create(
                        "https://api-code.albamon.com/codes/areas/korean/dongs/gucode?code=" + code))
                        .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("source unavailable");
                return response.body();
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("neighborhood lookup failed", error);
            }
        };
    }

    PartTimeNeighborhoods(Function<String, String> loader) { this.loader = loader; }

    List<Neighborhood> list(String districtCode) {
        if (districtCode == null || !districtCode.matches("[A-Z0-9]{4}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시·군·구를 다시 선택해 주세요.");
        var cached = cache.get(districtCode);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.items();
        try {
            var data = new ObjectMapper().readTree(loader.apply(districtCode));
            if (!data.isArray()) throw new IllegalStateException("unrecognized code response");
            var items = new java.util.ArrayList<Neighborhood>();
            var seen = new java.util.HashSet<String>();
            for (var item : data) {
                String id = item.path("code").asText(), name = item.path("name").asText();
                if (!id.matches("[A-Z0-9]{8}") || !id.startsWith(districtCode) || name.isBlank()
                        || name.length() > 40 || !seen.add(id))
                    throw new IllegalStateException("invalid neighborhood data");
                items.add(new Neighborhood(id, name));
            }
            var result = List.copyOf(items);
            cache.put(districtCode, new Cached(result, Instant.now().plus(Duration.ofHours(24))));
            return result;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "읍·면·동 목록을 불러오지 못했습니다. 다시 시도하거나 시·군·구 전체로 검색해 주세요.");
        }
    }
}
