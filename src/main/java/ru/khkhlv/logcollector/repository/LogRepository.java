package ru.khkhlv.logcollector.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.khkhlv.logcollector.model.LogEntry;

import java.time.Instant;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<LogEntry, Long>,
        JpaSpecificationExecutor<LogEntry>,
        LogRepositoryCustom {

    @Query("SELECT l.level, COUNT(l) FROM LogEntry l " +
            "WHERE l.createdAt BETWEEN :from AND :to " +
            "GROUP BY l.level")
    List<Object[]> countLogsByLevel(Instant from, Instant to);

    /**
     * Количество логов по источникам за период.
     */
    @Query("SELECT l.source, COUNT(l) FROM LogEntry l " +
            "WHERE l.createdAt BETWEEN :from AND :to " +
            "GROUP BY l.source")
    List<Object[]> countLogsBySource(Instant from, Instant to);

    /**
     * Топ типов ошибок по полю payload.error_type (PostgreSQL JSONB).
     */
    @Query(value = """
            SELECT COALESCE(payload->>'error_type', 'UNKNOWN') AS error_type, COUNT(*) AS cnt
            FROM logs
            WHERE created_at BETWEEN :from AND :to
            GROUP BY error_type
            ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> topErrorTypes(Instant from, Instant to);

    /**
     * Источники, которые не писали логи позже указанного момента времени.
     */
    @Query("SELECT DISTINCT l.source FROM LogEntry l " +
            "GROUP BY l.source " +
            "HAVING MAX(l.createdAt) < :threshold")
    List<String> findDeadSources(Instant threshold);

    Page<LogEntry> findBySourceAndLevel(String source, String level, Pageable pageable);
}
