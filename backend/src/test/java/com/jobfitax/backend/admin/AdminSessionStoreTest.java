package com.jobfitax.backend.admin;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminSessionStoreTest {
    @Test void rememberedSessionWorksAcrossAuthInstancesAndLogoutRevokesIt() {
        var store = new AdminSessionStore();
        var first = new AdminAuth("tester","test-password",store);
        String token = first.login("tester","test-password",true);
        assertThat(Duration.between(Instant.now(),store.expiry(token)).toDays()).isBetween(29L,30L);
        new AdminAuth("tester","test-password",store).require("Bearer " + token);
        first.logout("Bearer " + token);
        assertThatThrownBy(() -> new AdminAuth("tester","test-password",store).require("Bearer " + token))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void regularAndExpiredSessionsHaveCorrectLifetimesAndOnlyHashesAreStored() {
        var store = new AdminSessionStore();
        var auth = new AdminAuth("tester","test-password",store);
        String token = auth.login("tester","test-password",false);
        assertThat(Duration.between(Instant.now(),store.expiry(token)).toHours()).isBetween(7L,8L);
        assertThat(AdminSessionStore.hash(token)).hasSize(64).doesNotContain(token);
        store.save(token,Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> auth.require("Bearer " + token)).isInstanceOf(ResponseStatusException.class);
    }
}
