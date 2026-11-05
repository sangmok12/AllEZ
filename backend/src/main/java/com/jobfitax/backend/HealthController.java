package com.jobfitax.backend;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** No database, usage tracking, or upstream search is involved. */
@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }
}
