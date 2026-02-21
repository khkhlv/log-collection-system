package ru.khkhlv.logcollector.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.khkhlv.logcollector.service.metrics.LogMetricsCollector;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final LogMetricsCollector metricsCollector;

    public DebugController(LogMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @PostMapping("/test-ingestion")
    public String testIngestion(@RequestParam int count) {
        metricsCollector.recordIngestion(count, 100);
        return "Recorded " + count + " ingestion metrics";
    }
}