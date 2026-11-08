package com.jobfitax.backend.jobsearch;

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
    private final Map<String, List<Neighborhood>> bundled;

    PartTimeNeighborhoods() {
        loader = null;
        try (var input = getClass().getResourceAsStream("/part-time/neighborhoods.json")) {
            if (input == null) throw new IllegalStateException("Missing neighborhood codes");
            var regions = new ObjectMapper().readTree(input).path("regions");
            if (!regions.isObject() || regions.isEmpty()) throw new IllegalStateException("Invalid neighborhood codes");
            var loaded = new java.util.HashMap<String, List<Neighborhood>>();
            for (var entry : regions.properties()) {
                String code = entry.getKey().substring(entry.getKey().lastIndexOf('_') + 1);
                if (!code.matches("[A-Z0-9]{4}") || loaded.containsKey(code))
                    throw new IllegalStateException("Invalid district code");
                loaded.put(code, parse(code, entry.getValue(), "id"));
            }
            bundled = Map.copyOf(loaded);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load bundled neighborhood codes", error);
        }
    }

    PartTimeNeighborhoods(Function<String, String> loader) { this.loader = loader; bundled = Map.of(); }

    List<Neighborhood> list(String districtCode) {
        if (districtCode == null || !districtCode.matches("[A-Z0-9]{4}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시·군·구를 다시 선택해 주세요.");
        if (loader == null) {
            var items = bundled.get(districtCode);
            if (items == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시·군·구를 다시 선택해 주세요.");
            return items;
        }
        var cached = cache.get(districtCode);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.items();
        try {
            var result = parse(districtCode, new ObjectMapper().readTree(loader.apply(districtCode)), "code");
            cache.put(districtCode, new Cached(result, Instant.now().plus(Duration.ofHours(24))));
            return result;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "읍·면·동 목록을 불러오지 못했습니다. 다시 시도하거나 시·군·구 전체로 검색해 주세요.");
        }
    }

    private static List<Neighborhood> parse(String districtCode, tools.jackson.databind.JsonNode data, String codeField) {
        if (!data.isArray()) throw new IllegalStateException("unrecognized code response");
        var items = new java.util.ArrayList<Neighborhood>();
        var seen = new java.util.HashSet<String>();
        for (var item : data) {
            String id = item.path(codeField).asText(), name = item.path("name").asText();
            if (!id.matches("[A-Z0-9]{8}") || !id.startsWith(districtCode) || name.isBlank()
                    || name.length() > 40 || !seen.add(id))
                throw new IllegalStateException("invalid neighborhood data");
            items.add(new Neighborhood(id, name));
        }
        return List.copyOf(items);
    }
}
