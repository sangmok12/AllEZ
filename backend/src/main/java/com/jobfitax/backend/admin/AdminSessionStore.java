package com.jobfitax.backend.admin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminSessionStore {
    private final URI endpoint;
    private final String key;
    private final ObjectMapper mapper;
    private final Map<String, Instant> memory;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Autowired
    public AdminSessionStore(@Value("${SUPABASE_URL:}") String url,
                             @Value("${SUPABASE_SECRET_KEY:}") String key, ObjectMapper mapper) {
        URI target = null;
        try {
            URI base = URI.create(url.strip());
            if ("https".equals(base.getScheme()) && base.getHost() != null && base.getHost().endsWith(".supabase.co")
                    && base.getUserInfo() == null && base.getQuery() == null
                    && (base.getPath().isEmpty() || "/".equals(base.getPath()))) {
                target = base.resolve("/rest/v1/admin_sessions");
            }
        } catch (IllegalArgumentException ignored) { }
        this.endpoint = target; this.key = key.strip(); this.mapper = mapper; this.memory = null;
    }

    /** In-memory implementation for isolated unit tests only. */
    AdminSessionStore() { endpoint = null; key = ""; mapper = null; memory = new ConcurrentHashMap<>(); }

    public void save(String token, Instant expiry) {
        String hash = hash(token);
        if (memory != null) { memory.put(hash, expiry); return; }
        try {
            String body = mapper.writeValueAsString(Map.of("token_hash",hash,"expires_at",expiry.toString()));
            exchange("", "POST", body);
        } catch (ResponseStatusException error) { throw error; }
        catch (Exception ignored) { throw unavailable(); }
    }

    public Instant expiry(String token) {
        String hash = hash(token);
        if (memory != null) return memory.get(hash);
        String body = exchange("?token_hash=eq." + hash + "&select=expires_at&limit=1", "GET", null);
        try {
            List<?> rows = mapper.readValue(body, List.class);
            return rows.isEmpty() ? null : Instant.parse(((Map<?, ?>) rows.getFirst()).get("expires_at").toString());
        } catch (Exception ignored) { throw unavailable(); }
    }

    public void remove(String token) {
        String hash = hash(token);
        if (memory != null) { memory.remove(hash); return; }
        exchange("?token_hash=eq." + hash, "DELETE", null);
    }

    private String exchange(String query, String method, String body) {
        if (endpoint == null || key.isBlank()) throw unavailable();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                var builder = HttpRequest.newBuilder(URI.create(endpoint + query))
                        .timeout(Duration.ofSeconds(15)).header("apikey", key)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal,resolution=ignore-duplicates");
                builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
                if (response.statusCode() < 500 && response.statusCode() != 429) throw unavailable();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt(); throw unavailable();
            } catch (ResponseStatusException error) { throw error; }
            catch (Exception ignored) { /* Retry transient failures without logging tokens. */ }
        }
        throw unavailable();
    }

    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ignored) { throw new IllegalStateException("Session hashing unavailable"); }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "로그인 저장소 연결이 늦어지고 있습니다. 다시 시도해 주세요.");
    }
}
