package com.jobfitax.backend.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuth {
    private static final long TTL = 8 * 60 * 60;
    private final String username;
    private final String password;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
    private long minute;
    private int attempts;

    public AdminAuth(@Value("${ADMIN_USERNAME:admin}") String username,
                     @Value("${ADMIN_PASSWORD:}") String password) {
        this.username = username;
        this.password = password;
    }

    public synchronized String login(String user, String pass) {
        if (password.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "관리자 로그인이 설정되지 않았습니다.");
        long current = System.currentTimeMillis() / 60000;
        if (minute != current) { minute = current; attempts = 0; }
        if (++attempts > 20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 로그인해 주세요.");
        boolean validUser = equal(username, user);
        boolean validPassword = equal(password, pass);
        if (!(validUser & validPassword)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호를 확인해 주세요.");
        sessions.entrySet().removeIf(entry -> !entry.getValue().isAfter(Instant.now()));
        if (sessions.size() >= 32) sessions.clear();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, Instant.now().plusSeconds(TTL));
        return token;
    }

    public void require(String authorization) {
        String token = token(authorization);
        Instant expiry = sessions.get(token);
        if (expiry == null || !expiry.isAfter(Instant.now())) {
            sessions.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    public void logout(String authorization) { sessions.remove(token(authorization)); }

    private static String token(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
    }

    private static boolean equal(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8));
    }
}
