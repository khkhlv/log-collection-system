package ru.khkhlv.logcollector.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopItem {
        private String key;
        private long count;
    }
}

