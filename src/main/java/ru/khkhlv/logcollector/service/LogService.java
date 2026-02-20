package ru.khkhlv.logcollector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.khkhlv.logcollector.controller.LogCreateRequest;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.repository.LogRepository;
import ru.khkhlv.logcollector.service.metrics.LogMetricsCollector;
import ru.khkhlv.logcollector.service.parser.LogParser;
import ru.khkhlv.logcollector.service.parser.LogParserFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LogService {

    private final LogRepository logRepository;
    private final LogParserFactory parserFactory;
    private final LogMetricsCollector metricsCollector;

    @Transactional
    public List<LogEntry> ingestLogs(List<LogCreateRequest> requests) {
        Instant startTime = Instant.now();

        List<LogEntry> entries = requests.stream()
                .map(this::mapToEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!entries.isEmpty()) {
            List<LogEntry> saved = logRepository.saveAll(entries);

            // Обновление метрик
            metricsCollector.recordIngestion(
                    saved.size(),
                    Duration.between(startTime, Instant.now()).toMillis()
            );

            log.debug("Saved {} log entries", saved.size());
            return saved;
        }
        return Collections.emptyList();
    }

    @Transactional
    public LogEntry ingestRawLog(String rawLog, String formatType, String source) {
        try {
            LogParser parser = parserFactory.getParser(formatType);
            LogEntry entry = parser.parse(rawLog);

            if (entry.getSource() == null) {
                entry.setSource(source);
            }
            entry.setFormatType(formatType);
            entry.setRawContent(rawLog);

            LogEntry saved = logRepository.save(entry);
            metricsCollector.recordIngestion(1, 0);
            return saved;
        } catch (Exception e) {
            log.error("Failed to parse {} log: {}", formatType, rawLog, e);
            metricsCollector.recordParsingError(formatType);
            throw e;
        }
    }

    private LogEntry mapToEntity(LogCreateRequest request) {
        try {
            return LogEntry.builder()
                    .createdAt(request.getCreatedAt())
                    .level(request.getLevel().toUpperCase())
                    .source(request.getSource())
                    .host(request.getHost())
                    .environment(request.getEnvironment())
                    .message(request.getMessage())
                    .payload(request.getPayload())
                    .receivedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.error("Failed to map request to entity", e);
            return null;
        }
    }

//
//    public Page<LogEntry> search(String message, String level, String source,
//                                 String host, String environment, Instant from, Instant to,
//                                 Map<String, String> payload, int page, int size) {
//
//        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
//        return logRepository.searchLogs(message, level, source, host, environment,
//                from, to, (Map<String, Object>) (Map<?, ?>) payload, pageable);
//    }
}