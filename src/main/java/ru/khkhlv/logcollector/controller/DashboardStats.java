package ru.khkhlv.logcollector.controller;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Slf4j
public class DashboardStats {

    // Явный конструктор с правильным порядком параметров
    public DashboardStats(Instant from, Instant to, long totalLogs, Map<String, Long> logsByLevel,
                          List<TopItem> topSources, List<TopItem> topErrorTypes, List<String> deadSources) {
        this.from = from;
        this.to = to;
        this.totalLogs = totalLogs;
        this.logsByLevel = logsByLevel;
        this.topSources = topSources;
        this.topErrorTypes = topErrorTypes;
        this.deadSources = deadSources;
    }

    private Instant from;
    private Instant to;

    /**
     * Общее количество логов за период.
     */
    private long totalLogs;

    /**
     * Распределение логов по уровням.
     */
    private Map<String, Long> logsByLevel;

    /**
     * Топ источников по количеству логов.
     */
    private List<TopItem> topSources;

    /**
     * Топ типов ошибок по количеству.
     */
    private List<TopItem> topErrorTypes;

    /**
     * Источники, которые не писали логи более N минут (dead services).
     */
    private List<String> deadSources;

    // Явный builder для совместимости
    public static DashboardStatsBuilder builder() {
        return new DashboardStatsBuilder();
    }

    public static class DashboardStatsBuilder {
        private Instant from;
        private Instant to;
        private long totalLogs;
        private Map<String, Long> logsByLevel;
        private List<TopItem> topSources;
        private List<TopItem> topErrorTypes;
        private List<String> deadSources;

        public DashboardStatsBuilder from(Instant from) {
            this.from = from;
            return this;
        }

        public DashboardStatsBuilder to(Instant to) {
            this.to = to;
            return this;
        }

        public DashboardStatsBuilder totalLogs(long totalLogs) {
            this.totalLogs = totalLogs;
            return this;
        }

        public DashboardStatsBuilder logsByLevel(Map<String, Long> logsByLevel) {
            this.logsByLevel = logsByLevel;
            return this;
        }

        public DashboardStatsBuilder topSources(List<TopItem> topSources) {
            this.topSources = topSources;
            return this;
        }

        public DashboardStatsBuilder topErrorTypes(List<TopItem> topErrorTypes) {
            this.topErrorTypes = topErrorTypes;
            return this;
        }

        public DashboardStatsBuilder deadSources(List<String> deadSources) {
            this.deadSources = deadSources;
            return this;
        }

        public DashboardStats build() {
            return new DashboardStats(from, to, totalLogs, logsByLevel, topSources, topErrorTypes, deadSources);
        }
    }

    public static class TopItem {
        private String key;
        private long count;

        public TopItem() {
        }

        public TopItem(String key, long count) {
            this.key = key;
            this.count = count;
        }

        // Явный конструктор для совместимости с Long
        public TopItem(String key, Long count) {
            this.key = key;
            this.count = count != null ? count : 0L;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }
}

