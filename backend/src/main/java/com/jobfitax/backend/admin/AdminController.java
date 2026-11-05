package com.jobfitax.backend.admin;

import java.time.LocalDate;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAuth auth;
    private final AdminReportService reports;

    public AdminController(AdminAuth auth, AdminReportService reports) { this.auth = auth; this.reports = reports; }

    @ModelAttribute
    public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    public record LoginRequest(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=200) String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return Map.of("token", auth.login(request.username(), request.password()), "expiresIn", 28800);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value="Authorization", required=false) String authorization) { auth.logout(authorization); }

    @GetMapping(value="/report", produces=MediaType.APPLICATION_JSON_VALUE)
    public String report(@RequestHeader(value="Authorization", required=false) String authorization,
                         @RequestParam("start") LocalDate start, @RequestParam("end") LocalDate end,
                         @RequestParam(value="category", defaultValue="all") String category,
                         @RequestParam(value="page", defaultValue="1") int page) {
        auth.require(authorization);
        return reports.report(start,end,category,page);
    }
}
