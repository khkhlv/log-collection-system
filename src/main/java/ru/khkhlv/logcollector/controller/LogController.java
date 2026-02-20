package ru.khkhlv.logcollector.controller;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.service.LogService;
import ru.khkhlv.logcollector.service.search.LogSearchService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/logs")
@Slf4j
@Timed("log.controller")
public class LogController {

    private final LogService logService;
    private final LogSearchService searchService;

    public LogController(LogService logService, LogSearchService searchService) {
        this.logService = logService;
        this.searchService = searchService;
    }

    /**
     * Добавление одного или нескольких логов
     * POST /api/v1/logs
     */
    @PostMapping
    @Timed(value = "log.ingest", description = "Time to ingest logs")
    public ResponseEntity<IngestionResponse> ingestLogs(
            @Valid @RequestBody List<LogCreateRequest> requests) {

        log.debug("Received {} log entries for ingestion", requests.size());

        List<LogEntry> saved = logService.ingestLogs(requests);

        return ResponseEntity.ok(
                IngestionResponse.builder()
                        .success(true)
                        .processedCount(saved.size())
                        .timestamp(Instant.now())
                        .build()
        );
    }

    /**
     * Поиск логов с фильтрами и пагинацией
     * GET /api/v1/logs/search
     */
    @GetMapping("/search")
    @Timed(value = "log.search", description = "Time to search logs")
    public ResponseEntity<LogSearchResponse> searchLogs(
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String host,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Map<String, String> payload,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        log.debug("Search request: level={}, source={}, from={}, to={}",
                level, source, from, to);

        Page<LogEntry> results = searchService.search(
                message, level, source, host, environment, from, to, payload, page, size
        );

        return ResponseEntity.ok(
                LogSearchResponse.builder()
                        .total(results.getTotalElements())
                        .page(results.getNumber())
                        .size(results.getSize())
                        .logs(results.getContent())
                        .build()
        );
    }

    /**
     * Статистика для дашборда
     * GET /api/v1/logs/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats(
            @RequestParam Instant from,
            @RequestParam Instant to) {

        return ResponseEntity.ok(searchService.getDashboardStats(from, to));
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "log-collector"));
    }
}