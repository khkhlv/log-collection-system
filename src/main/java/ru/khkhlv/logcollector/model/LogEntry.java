package ru.khkhlv.logcollector.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "logs", indexes = {
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_level_source", columnList = "level, source"),
        @Index(name = "idx_fulltext_message", columnList = "message")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "level", nullable = false, length = 20)
    private String level;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "environment", length = 50)
    private String environment;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "format_type", length = 20)
    private String formatType;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    // Явные геттеры и сеттеры для совместимости
    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setFormatType(String formatType) {
        this.formatType = formatType;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    // Явный builder для совместимости
    public static LogEntryBuilder builder() {
        return new LogEntryBuilder();
    }

    public static class LogEntryBuilder {
        private Long id;
        private Instant createdAt;
        private Instant receivedAt;
        private String level;
        private String source;
        private String host;
        private String environment;
        private String message;
        private Map<String, Object> payload;
        private String rawContent;
        private String formatType;

        public LogEntryBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public LogEntryBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public LogEntryBuilder receivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        public LogEntryBuilder level(String level) {
            this.level = level;
            return this;
        }

        public LogEntryBuilder source(String source) {
            this.source = source;
            return this;
        }

        public LogEntryBuilder host(String host) {
            this.host = host;
            return this;
        }

        public LogEntryBuilder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public LogEntryBuilder message(String message) {
            this.message = message;
            return this;
        }

        public LogEntryBuilder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public LogEntryBuilder rawContent(String rawContent) {
            this.rawContent = rawContent;
            return this;
        }

        public LogEntryBuilder formatType(String formatType) {
            this.formatType = formatType;
            return this;
        }

        public LogEntry build() {
            LogEntry entry = new LogEntry();
            entry.id = this.id;
            entry.createdAt = this.createdAt;
            entry.receivedAt = this.receivedAt;
            entry.level = this.level;
            entry.source = this.source;
            entry.host = this.host;
            entry.environment = this.environment;
            entry.message = this.message;
            entry.payload = this.payload;
            entry.rawContent = this.rawContent;
            entry.formatType = this.formatType;
            return entry;
        }
    }
}