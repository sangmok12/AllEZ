package com.jobfitax.backend.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminReportServiceTest {
    @Test void limitsRangeCategoryAndPagination() {
        var day = LocalDate.of(2026,11,5);
        AdminReportService.validate(day,day,"all",1);
        assertThatThrownBy(() -> AdminReportService.validate(day,day.minusDays(1),"all",1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AdminReportService.validate(day,day.plusDays(366),"all",1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AdminReportService.validate(day,day,"unknown",1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AdminReportService.validate(day,day,"jobs",0)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsUnauthenticatedReportBeforeDatabaseAccess() {
        var service = mock(AdminReportService.class);
        var controller = new AdminController(new AdminAuth("tester","test-password"),service);
        assertThatThrownBy(() -> controller.report(null,LocalDate.now(),LocalDate.now(),"all",1))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(service);
    }
}
