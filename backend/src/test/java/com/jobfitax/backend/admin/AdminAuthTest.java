package com.jobfitax.backend.admin;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminAuthTest {
    @Test void deniesAnonymousAndWrongCredentialsAndRevokesLogoutToken() {
        var auth = new AdminAuth("tester", "test-password");
        assertThatThrownBy(() -> auth.require(null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> auth.login("tester", "wrong")).isInstanceOf(ResponseStatusException.class);
        String token = auth.login("tester", "test-password");
        auth.require("Bearer " + token);
        assertThat(token).hasSizeGreaterThan(30).doesNotContain("test-password");
        auth.logout("Bearer " + token);
        assertThatThrownBy(() -> auth.require("Bearer " + token)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void emptyConfigurationFailsClosedAndRepeatedFailuresAreLimited() {
        assertThatThrownBy(() -> new AdminAuth("tester", "").login("tester", ""))
                .isInstanceOf(ResponseStatusException.class);
        var auth = new AdminAuth("tester", "test-password");
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> auth.login("tester", "bad")).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> auth.login("tester", "test-password")).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(429));
    }
}
