package ru.khkhlv.logcollector.service.etl;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.repository.LogRepository;
import ru.khkhlv.logcollector.service.parser.LogParserFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EtlTask {

    private final LogRepository logRepository;
    private final LogParserFactory parserFactory;

    public EtlTask(LogRepository logRepository, LogParserFactory parserFactory) {
        this.logRepository = logRepository;
        this.parserFactory = parserFactory;
    }

    public List<String> execute(String sourceType, Instant from, Instant to) {
        // Загрузка сырых логов из источника
        // В реальном проекте: чтение из S3, Kafka, файлов и т.д.
        return new ArrayList<>();
    }

    public List<TransformedLog> transform(List<String> rawLogs) {
        return rawLogs.stream()
                .map(this::parseAndValidate)
                .filter(Objects::nonNull)
                .distinct() // Удаление дубликатов
                .collect(Collectors.toList());
    }

    @Transactional
    public int load(List<TransformedLog> logs) {
        List<LogEntry> entities = logs.stream()
                .map(TransformedLog::toEntity)
                .collect(Collectors.toList());

        if (!entities.isEmpty()) {
            logRepository.saveAll(entities);
        }
        return entities.size();
    }

    private TransformedLog parseAndValidate(String rawLog) {
        try {
            var parser = parserFactory.detectParser(rawLog);
            LogEntry entry = parser.parse(rawLog);

            // Валидация обязательных полей
            if (entry.getCreatedAt() == null || entry.getMessage() == null) {
                return null;
            }

            return TransformedLog.from(entry);
        } catch (Exception e) {
            return null; // Пропускаем невалидные
        }
    }

    public static class TransformedLog {
        private Instant createdAt;
        private String level;
        private String source;
        private String message;
        private Map<String, Object> payload;

        public TransformedLog(Instant createdAt, String level, String source, String message, Map<String, Object> payload) {
            this.createdAt = createdAt;
            this.level = level;
            this.source = source;
            this.message = message;
            this.payload = payload;
        }

        public static TransformedLogBuilder builder() {
            return new TransformedLogBuilder();
        }

        public static TransformedLog from(LogEntry entry) {
            return new TransformedLog(
                    entry.getCreatedAt(),
                    entry.getLevel(),
                    entry.getSource(),
                    entry.getMessage(),
                    entry.getPayload()
            );
        }

        public LogEntry toEntity() {
            return LogEntry.builder()
                    .createdAt(createdAt)
                    .level(level)
                    .source(source)
                    .message(message)
                    .payload(payload)
                    .receivedAt(Instant.now())
                    .build();
        }

        public static class TransformedLogBuilder {
            private Instant createdAt;
            private String level;
            private String source;
            private String message;
            private Map<String, Object> payload;

            public TransformedLogBuilder createdAt(Instant createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public TransformedLogBuilder level(String level) {
                this.level = level;
                return this;
            }

            public TransformedLogBuilder source(String source) {
                this.source = source;
                return this;
            }

            public TransformedLogBuilder message(String message) {
                this.message = message;
                return this;
            }

            public TransformedLogBuilder payload(Map<String, Object> payload) {
                this.payload = payload;
                return this;
            }

            public TransformedLog build() {
                return new TransformedLog(createdAt, level, source, message, payload);
            }
        }
    }
}