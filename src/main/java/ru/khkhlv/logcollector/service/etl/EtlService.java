package ru.khkhlv.logcollector.service.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.repository.LogRepository;
import ru.khkhlv.logcollector.service.metrics.LogMetricsCollector;
import ru.khkhlv.logcollector.service.parser.LogParser;
import ru.khkhlv.logcollector.service.parser.LogParserFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class EtlService {

    private final LogRepository logRepository;
    private final LogParserFactory parserFactory;
    private final LogMetricsCollector metricsCollector;
    private final ObjectMapper objectMapper;

    @Value("${etl.validation.required-fields:level,source,message}")
    private String[] requiredFields;

    @Value("${etl.deduplication.enabled:true}")
    private boolean deduplicationEnabled;

    @Value("${etl.deduplication.window-seconds:60}")
    private int deduplicationWindowSeconds;

    // Для дедупликации используем ConcurrentHashMap с ограничением размера
    private final Map<String, Long> recentMessageHashes = new ConcurrentHashMap<>() {
        @Override
        public Long put(String key, Long value) {
            // Ограничиваем размер карты
            if (size() > 10000) {
                // Удаляем самую старую запись
                Optional<String> oldestKey = keySet().stream().findFirst();
                oldestKey.ifPresent(this::remove);
            }
            return super.put(key, value);
        }
    };

    // Счетчики для метрик
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    private final AtomicLong validationErrorCount = new AtomicLong(0);

    public EtlService(
            LogRepository logRepository,
            LogParserFactory parserFactory,
            LogMetricsCollector metricsCollector) {
        this.logRepository = logRepository;
        this.parserFactory = parserFactory;
        this.metricsCollector = metricsCollector;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Основной метод ETL обработки одного лога
     */
    @Transactional
    public LogEntry processLog(String rawLog, String format, String source) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Парсинг
            LogEntry entry = parseLog(rawLog, format, source);

            // 2. Валидация
            validateLog(entry, rawLog);

            // 3. Дедупликация
            if (deduplicationEnabled) {
                checkForDuplicate(entry);
            }

            // 4. Очистка и обогащение
            enrichLog(entry);

            // 5. Сохранение в БД
            LogEntry saved = logRepository.save(entry);

            // 6. Обновление метрик
            updateMetrics(saved, format, System.currentTimeMillis() - startTime);


            log.info("ETL processed log: id={}, level={}, source={}",
                    saved.getId(), saved.getLevel(), saved.getSource());

            return saved;

        } catch (DuplicateLogException e) {
            metricsCollector.recordDuplicate(source, "unknown");
            throw e;
        } catch (ValidationException e) {
            metricsCollector.recordEtlError("validation", format, source);
            throw e;
        } catch (Exception e) {
            metricsCollector.recordEtlError("processing", format, source);
            throw new RuntimeException();
        }
    }

    /**
     * Пакетная обработка нескольких логов (для файлов)
     */
    @Transactional
    public List<LogEntry> processLogs(List<String> rawLogs, String format, String source) {
        if (rawLogs.isEmpty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();
        List<LogEntry> processed = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        for (String rawLog : rawLogs) {
            try {
                LogEntry entry = processLog(rawLog, format, source);
                if (entry != null) {
                    processed.add(entry);
                    successCount++;
                }
            } catch (Exception e) {
                errorCount++;
                // Ошибка уже залогирована в processLog
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Для батча используем recordIngestion с общим количеством
        if (successCount > 0) {
            metricsCollector.recordIngestion(successCount, duration);
        }

        log.info("Batch processed: success={}, errors={}, duration={}ms",
                successCount, errorCount, duration);

        return processed;
    }

    private LogEntry parseLog(String rawLog, String format, String source) throws Exception {
        LogParser parser = parserFactory.getParser(format);
        LogEntry entry = parser.parse(rawLog);

        if (entry.getSource() == null) {
            entry.setSource(source);
        }
        entry.setFormatType(format);
        entry.setRawContent(rawLog);

        return entry;
    }

    private void validateLog(LogEntry entry, String rawLog) throws ValidationException {
        List<String> missingFields = new ArrayList<>();

        // Проверка обязательных полей
        for (String field : requiredFields) {
            switch (field) {
                case "level":
                    if (entry.getLevel() == null || entry.getLevel().trim().isEmpty()) {
                        missingFields.add("level");
                    }
                    break;
                case "source":
                    if (entry.getSource() == null || entry.getSource().trim().isEmpty()) {
                        missingFields.add("source");
                    }
                    break;
                case "message":
                    if (entry.getMessage() == null || entry.getMessage().trim().isEmpty()) {
                        missingFields.add("message");
                    }
                    break;
                case "createdAt":
                    if (entry.getCreatedAt() == null) {
                        missingFields.add("createdAt");
                    }
                    break;
            }
        }

        if (!missingFields.isEmpty()) {
            throw new ValidationException("Missing required fields: " + missingFields);
        }

        // Валидация уровня логирования
        if (entry.getLevel() != null) {
            String level = entry.getLevel().toUpperCase();
            if (!List.of("INFO", "WARN", "ERROR", "DEBUG", "TRACE", "FATAL").contains(level)) {
                throw new ValidationException("Invalid log level: " + entry.getLevel());
            }
        }

        // Проверка даты (не в будущем)
        if (entry.getCreatedAt() != null && entry.getCreatedAt().isAfter(Instant.now().plusSeconds(10))) {
            throw new ValidationException("CreatedAt is in the future: " + entry.getCreatedAt());
        }

        // Проверка длины сообщения
        if (entry.getMessage() != null && entry.getMessage().length() > 10000) {
            entry.setMessage(entry.getMessage().substring(0, 10000) + "... (truncated)");
        }
    }

    private void checkForDuplicate(LogEntry entry) throws DuplicateLogException {
        // Создаем хеш на основе source, message и createdAt
        String messageHash = generateMessageHash(entry);

        // Проверяем в кэше
        if (recentMessageHashes.containsKey(messageHash)) {
            // Проверяем, не устарела ли запись
            long timestamp = recentMessageHashes.get(messageHash);
            if (System.currentTimeMillis() - timestamp < deduplicationWindowSeconds * 1000) {
                throw new DuplicateLogException("Duplicate log detected (cache)");
            }
        }

        // Проверка по БД за последние N секунд
        if (entry.getSource() != null && entry.getMessage() != null) {
            Instant threshold = Instant.now().minusSeconds(deduplicationWindowSeconds);
            long count = logRepository.countBySourceAndMessageAndCreatedAtAfter(
                    entry.getSource(), entry.getMessage(), threshold
            );
            if (count > 0) {
                throw new DuplicateLogException("Duplicate log from source " + entry.getSource() + " (database)");
            }
        }

        // Добавляем в кэш
        recentMessageHashes.put(messageHash, System.currentTimeMillis());
    }

    private void updateMetrics(LogEntry entry, String format, long duration) {
        // Основная метрика ингестии
        metricsCollector.recordIngestion(1, duration);

        // Метрики по уровню, источнику и окружению
        metricsCollector.recordLogByLevel(
                entry.getLevel() != null ? entry.getLevel() : "UNKNOWN",
                entry.getSource() != null ? entry.getSource() : "unknown",
                entry.getEnvironment() != null ? entry.getEnvironment() : "unknown"
        );

        // Размер сообщения
        if (entry.getRawContent() != null) {
            metricsCollector.recordMessageSize(
                    entry.getRawContent().getBytes().length,
                    format
            );
        }

        // Задержка доставки
        if (entry.getCreatedAt() != null && entry.getReceivedAt() != null) {
            long lag = Duration.between(entry.getCreatedAt(), entry.getReceivedAt()).toMillis();
            if (lag > 0) {
                metricsCollector.recordDeliveryLag(lag, entry.getSource());
            }
        }
    }

    private void enrichLog(LogEntry entry) {
        // Нормализуем уровень
        if (entry.getLevel() != null) {
            entry.setLevel(entry.getLevel().toUpperCase());
        }

        // Добавляем метаданные в payload, если нужно
        if (entry.getPayload() == null) {
            entry.setPayload(new HashMap<>());
        }
    }

    private String generateMessageHash(LogEntry entry) {
        return Integer.toHexString((
                (entry.getSource() != null ? entry.getSource() : "") + ":" +
                        (entry.getMessage() != null ? entry.getMessage() : "") + ":" +
                        (entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : "")
        ).hashCode());
    }

    /**
     * Получение статистики ETL
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("processed", processedCount.get());
        stats.put("duplicates", duplicateCount.get());
        stats.put("validation_errors", validationErrorCount.get());
        stats.put("cache_size", recentMessageHashes.size());
        stats.put("total_processed", processedCount.get() + duplicateCount.get() + validationErrorCount.get());
        return stats;
    }

    /**
     * Очистка устаревших записей из кэша (можно вызывать по расписанию)
     */
    @Scheduled(fixedDelay = 60000) // Каждую минуту
    public void cleanupCache() {
        long now = System.currentTimeMillis();
        long expiryTime = now - (deduplicationWindowSeconds * 1000);

        recentMessageHashes.entrySet().removeIf(entry ->
                entry.getValue() < expiryTime
        );

        log.debug("Cleaned up cache. Size: {}", recentMessageHashes.size());
    }

    // Специальные исключения
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class DuplicateLogException extends RuntimeException {
        public DuplicateLogException(String message) {
            super(message);
        }
    }
}