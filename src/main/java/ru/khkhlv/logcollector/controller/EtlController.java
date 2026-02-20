package ru.khkhlv.logcollector.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.khkhlv.logcollector.service.etl.EtlSchedulerService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/etl")
@RequiredArgsConstructor
public class EtlController {

    private final EtlSchedulerService etlSchedulerService;

    /**
     * Запуск ETL за последние N часов
     */
    @PostMapping("/run/{hours}")
    public ResponseEntity<Map<String, String>> runEtl(@PathVariable int hours) {
        etlSchedulerService.runManualEtl(hours);
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "ETL pipeline started for last " + hours + " hours"
        ));
    }

    /**
     * Запуск ETL за конкретный период
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runEtl(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        etlSchedulerService.runManualEtl(from, to);
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "ETL pipeline started for period: " + from + " to " + to
        ));
    }

    /**
     * Получение статуса ETL (можно добавить мониторинг)
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "active",
                "lastRun", "2026-02-20T00:00:00Z", // Здесь можно хранить реальное значение
                "nextRun", "2026-02-20T01:00:00Z"
        ));
    }
}
