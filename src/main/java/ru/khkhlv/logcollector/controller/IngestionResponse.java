package ru.khkhlv.logcollector.controller;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Data
@NoArgsConstructor
@Slf4j
public class IngestionResponse {
    private boolean success;
    private int processedCount;
    private Instant timestamp;

    public IngestionResponse(boolean success, int processedCount, Instant timestamp) {
        this.success = success;
        this.processedCount = processedCount;
        this.timestamp = timestamp;
    }

    // Явный builder для совместимости
    public static IngestionResponseBuilder builder() {
        return new IngestionResponseBuilder();
    }

    public static class IngestionResponseBuilder {
        private boolean success;
        private int processedCount;
        private Instant timestamp;

        public IngestionResponseBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public IngestionResponseBuilder processedCount(int processedCount) {
            this.processedCount = processedCount;
            return this;
        }

        public IngestionResponseBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public IngestionResponse build() {
            return new IngestionResponse(success, processedCount, timestamp);
        }
    }
}

