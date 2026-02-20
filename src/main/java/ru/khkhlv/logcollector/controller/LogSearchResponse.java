package ru.khkhlv.logcollector.controller;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.khkhlv.logcollector.model.LogEntry;

import java.util.List;

@Data
@NoArgsConstructor
public class LogSearchResponse {
    private long total;
    private int page;
    private int size;
    private List<LogEntry> logs;

    public LogSearchResponse(long total, int page, int size, List<LogEntry> logs) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.logs = logs;
    }

    // Явный builder для совместимости
    public static LogSearchResponseBuilder builder() {
        return new LogSearchResponseBuilder();
    }

    public static class LogSearchResponseBuilder {
        private long total;
        private int page;
        private int size;
        private List<LogEntry> logs;

        public LogSearchResponseBuilder total(long total) {
            this.total = total;
            return this;
        }

        public LogSearchResponseBuilder page(int page) {
            this.page = page;
            return this;
        }

        public LogSearchResponseBuilder size(int size) {
            this.size = size;
            return this;
        }

        public LogSearchResponseBuilder logs(List<LogEntry> logs) {
            this.logs = logs;
            return this;
        }

        public LogSearchResponse build() {
            return new LogSearchResponse(total, page, size, logs);
        }
    }
}

