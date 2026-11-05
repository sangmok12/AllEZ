package com.jobfitax.backend;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test void healthIsIndependentOfAnalyticsAndDatabase() {
        assertThat(new HealthController().health()).containsEntry("status", "ok").hasSize(1);
    }
}
