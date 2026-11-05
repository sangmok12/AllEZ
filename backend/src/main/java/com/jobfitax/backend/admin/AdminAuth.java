package com.jobfitax.backend.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AdminSessionStore sessions;
    private long minute;
    private int attempts;

    @Autowired
    public AdminAuth(@Value("${ADMIN_USERNAME:admin}") String username,
                     @Value("${ADMIN_PASSWORD:}") String password, AdminSessionStore sessions) {
        this.username = username;
        this.password = password;
        this.sessions = sessions;
    }

    public AdminAuth(String username, String password) { this(username, password, new AdminSessionStore()); }

    public String login(String user, String pass) { return login(user, pass, false); }

    public synchronized String login(String user, String pass, boolean rememberMe) {
        if (password.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "관리자 로그인이 설정되지 않았습니다.");
        long current = System.currentTimeMillis() / 60000;
        if (minute != current) { minute = current; attempts = 0; }
        if (++attempts > 20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 로그인해 주세요.");
        boolean validUser = equal(username, user);
        boolean validPassword = equal(password, pass);
        if (!(validUser & validPassword)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호를 확인해 주세요.");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(token, Instant.now().plusSeconds(rememberMe ? 30L * 24 * 60 * 60 : TTL));
        return token;
    }

    public void require(String authorization) {
        String token = token(authorization);
        if (token.isBlank() || token.length() > 100) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        Instant expiry = sessions.expiry(token);
        if (expiry == null || !expiry.isAfter(Instant.now())) {
            if (expiry != null) sessions.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    public void logout(String authorization) {
        String token = token(authorization);
        if (!token.isBlank() && token.length() <= 100) sessions.remove(token);
    }

    private static String token(String header) {
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
    }

    private static boolean equal(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8));
    }
}
