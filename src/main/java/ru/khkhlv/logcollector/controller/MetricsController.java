package ru.khkhlv.logcollector.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.khkhlv.logcollector.service.metrics.LogMetricsCollector;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final LogMetricsCollector metricsCollector;

    @GetMapping("/aggregated")
    public Map<String, Object> getAggregatedMetrics() {
        return metricsCollector.getAggregatedMetrics();
    }

    @GetMapping("/rate")
    public Map<String, Double> getCurrentRate() {
        return Map.of("current_rate", metricsCollector.getCurrentRate());
    }
}