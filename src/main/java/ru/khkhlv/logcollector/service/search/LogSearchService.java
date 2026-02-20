package ru.khkhlv.logcollector.service.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.khkhlv.logcollector.controller.DashboardStats;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.repository.LogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class LogSearchService {

    private final LogRepository logRepository;

    public LogSearchService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public Page<LogEntry> search(
            String message,
            String level,
            String source,
            String host,
            String environment,
            Instant from,
            Instant to,
            Map<String, String> payload,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        final Map<String, Object> payloadObj;
        if (payload != null && !payload.isEmpty()) {
            payloadObj = new java.util.HashMap<>();
            payload.forEach((k, v) -> payloadObj.put(k, v));
        } else {
            payloadObj = null;
        }

        return logRepository.searchLogs(
                message, level, source, host, environment,
                from, to, payloadObj, pageable
        );
    }

    public DashboardStats getDashboardStats(Instant from, Instant to) {
        long total = logRepository.countByCriteria(null, null, from, to);
        List<Object[]> byLevel = logRepository.countLogsByLevel(from, to);
        List<Object[]> topSourcesRaw = logRepository.countLogsBySource(from, to);

        Map<String, Long> logsByLevel = convertToMap(byLevel);
        List<DashboardStats.TopItem> topSources = topSourcesRaw.stream()
                .map(r -> new DashboardStats.TopItem((String) r[0], (Long) r[1]))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());

        List<Object[]> topErrorTypesRaw = logRepository.topErrorTypes(from, to);
        List<DashboardStats.TopItem> topErrorTypes = topErrorTypesRaw.stream()
                .map(r -> new DashboardStats.TopItem((String) r[0], (Long) r[1]))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(3)
                .collect(java.util.stream.Collectors.toList());

        Instant threshold = to.minus(java.time.Duration.ofHours(1));
        List<String> deadSources = logRepository.findDeadSources(threshold);

        return DashboardStats.builder()
                .from(from)
                .to(to)
                .totalLogs(total)
                .logsByLevel(logsByLevel)
                .topSources(topSources)
                .topErrorTypes(topErrorTypes)
                .deadSources(deadSources)
                .build();
    }

    private Map<String, Long> convertToMap(List<Object[]> data) {
        return data.stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> row[0].toString(),
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private double calculateErrorRate(Instant from, Instant to) {
        long total = logRepository.countByCriteria(null, null, from, to);
        long errors = logRepository.countByCriteria("ERROR", null, from, to);
        return total > 0 ? (double) errors / total * 100 : 0;
    }

    @Transactional(readOnly = true)
    public Optional<LogEntry> findById(Long id) {
        return logRepository.findById(id);
    }
}