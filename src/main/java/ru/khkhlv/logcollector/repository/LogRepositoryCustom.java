package ru.khkhlv.logcollector.repository;

import ru.khkhlv.logcollector.model.LogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Map;

public interface LogRepositoryCustom {

    Page<LogEntry> searchLogs(
            String messageContains,
            String level,
            String source,
            String host,
            String environment,
            Instant from,
            Instant to,
            Map<String, Object> payloadFilters,
            Pageable pageable
    );

    long countByCriteria(
            String level,
            String source,
            Instant from,
            Instant to
    );
}