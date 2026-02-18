package ru.khkhlv.logcollector.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.khkhlv.logcollector.controller.DashboardStats;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.repository.LogRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogSearchService {

    private final LogRepository logRepository;

    @Transactional(readOnly = true)
    public Page<LogEntry> search(
            String message,
            String level,
            String source,
            String host,
            String environment,
            Instant from,
            Instant to,
            Map<String, String> payloadFilters,
            int page,
            int size
    ) {
        Map<String, Object> payload = null;
        if (payloadFilters != null && !payloadFilters.isEmpty()) {
            payload = new HashMap<>(payloadFilters);
        }

        return logRepository.searchLogs(
                message,
                level,
                source,
                host,
                environment,
                from,
                to,
                payload,
                PageRequest.of(page, size)
        );
    }

    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats(Instant from, Instant to) {
        long total = logRepository.countByCriteria(null, null, from, to);

        // Распределение по уровням
        Map<String, Long> byLevel = logRepository.countLogsByLevel(from, to)
                .stream()
                .collect(Collectors.toMap(
                        row -> ((String) row[0]),
                        row -> (Long) row[1]
                ));

        // Топ-источники (по всем уровням)
        List<Object[]> topSourcesRaw = logRepository.countLogsBySource(from, to);
        List<DashboardStats.TopItem> topSources = topSourcesRaw.stream()
                .map(r -> new DashboardStats.TopItem((String) r[0], (Long) r[1]))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(10)
                .collect(Collectors.toList());

        // Топ типов ошибок по полю payload.error_type
        List<Object[]> topErrorTypesRaw = logRepository.topErrorTypes(from, to);
        List<DashboardStats.TopItem> topErrorTypes = topErrorTypesRaw.stream()
                .map(r -> new DashboardStats.TopItem((String) r[0], (Long) r[1]))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(3)
                .collect(Collectors.toList());

        // Dead services: источники, которые не писали логи больше часа от "to"
        Instant threshold = to.minus(Duration.ofHours(1));
        List<String> deadSources = logRepository.findDeadSources(threshold);

        // Сохраняем порядок уровней (INFO, WARNING, ERROR ...) при необходимости
        Map<String, Long> orderedByLevel = new LinkedHashMap<>();
        byLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> orderedByLevel.put(e.getKey(), e.getValue()));

        return DashboardStats.builder()
                .from(from)
                .to(to)
                .totalLogs(total)
                .logsByLevel(orderedByLevel)
                .topSources(topSources)
                .topErrorTypes(topErrorTypes)
                .deadSources(deadSources)
                .build();
    }
}

