package ru.khkhlv.logcollector.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class LogMetricsCollector {

    private final MeterRegistry meterRegistry;

    // Счетчики для агрегации в памяти (для real-time метрик)
    private final ConcurrentHashMap<String, AtomicLong> totalCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> errorCounters = new ConcurrentHashMap<>();

    // Окна для агрегации (1 мин, 5 мин, 15 мин, 1 час)
    private final long[] windows = {60, 300, 900, 3600};
    private final ConcurrentHashMap<String, SlidingWindowCounter> windowCounters = new ConcurrentHashMap<>();

    public LogMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Общее количество обработанных логов
        Counter.builder("logs.ingested.total")
                .description("Total number of logs processed")
                .tag("type", "all")
                .register(meterRegistry);

        // Количество логов по уровням
        for (String level : new String[]{"INFO", "WARN", "ERROR", "DEBUG", "TRACE", "FATAL"}) {
            Counter.builder("logs.by.level")
                    .description("Logs by level")
                    .tag("level", level)
                    .register(meterRegistry);

            // Инициализируем счетчики в памяти
            totalCounters.put("level_" + level, new AtomicLong(0));
        }

        // Количество логов по источникам (динамические теги)
        Counter.builder("logs.by.source")
                .description("Logs by source")
                .tag("source", "unknown")
                .register(meterRegistry);

        // Количество логов по окружению
        for (String env : new String[]{"production", "staging", "development", "test"}) {
            Counter.builder("logs.by.environment")
                    .description("Logs by environment")
                    .tag("environment", env)
                    .register(meterRegistry);
        }

        // Метрики производительности ETL
        Timer.builder("logs.ingestion.batch.duration.ms")
                .description("ETL processing time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .sla(Duration.ofMillis(10), Duration.ofMillis(50),
                        Duration.ofMillis(100), Duration.ofMillis(500))
                .register(meterRegistry);

        // Размер батчей
        DistributionSummary.builder("logs.ingestion.batch.size")
                .description("ETL batch size distribution")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .minimumExpectedValue(1.0)
                .maximumExpectedValue(10000.0)
                .register(meterRegistry);

        // Счетчики ошибок по типам
        Counter.builder("logs.parsing.errors.total")
                .description("ETL errors by type")
                .tag("type", "validation")
                .register(meterRegistry);

        Counter.builder("logs.parsing.errors.total")
                .description("ETL errors by type")
                .tag("type", "duplicate")
                .register(meterRegistry);

        Counter.builder("logs.parsing.errors.total")
                .description("ETL errors by type")
                .tag("type", "parsing")
                .register(meterRegistry);

        Counter.builder("logs.parsing.errors.total")
                .description("ETL errors by type")
                .tag("type", "database")
                .register(meterRegistry);

        // Инициализируем оконные счетчики
        for (long window : windows) {
            windowCounters.put("ingestion_" + window, new SlidingWindowCounter(window));
            windowCounters.put("errors_" + window, new SlidingWindowCounter(window));
        }
    }

    /**
     * Записывает метрики приёма логов
     */
    public void recordIngestion(int count, long durationMs) {
        // Общий счетчик
        Counter.builder("logs.ingested.total")
                .description("Total number of logs processed")
                .tag("application", "log-collector")
                .tag("type", "all")
                .register(meterRegistry)
                .increment(count);

        // Время обработки
        // Время обработки
        Timer.builder("logs.ingestion.batch.duration.ms")
                .description("Batch ingestion duration")
                .tag("application", "log-collector")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        // Размер батча
        DistributionSummary.builder("logs.ingestion.batch.size")
                .description("Batch size of ingested logs")
                .tag("application", "log-collector")
                .register(meterRegistry)
                .record(count);

        // Агрегация в окнах
        for (SlidingWindowCounter counter : windowCounters.values()) {
            if (counter.getName().startsWith("ingestion")) {
                counter.add(count);
            }
        }

        // Логируем метрики для отладки
        if (log.isDebugEnabled()) {
            log.debug("Ingestion metrics: count={}, duration={}ms, rate={}/s",
                    count, durationMs, getCurrentRate());
        }
    }

    /**
     * Записывает метрики лога по уровню
     */
    public void recordLogByLevel(String level, String source, String environment) {
        Counter.builder("logs.by.level")
                .description("Logs by level")
                .tag("application", "log-collector")
                .tag("level", level)
                .register(meterRegistry)
                .increment();

        Counter.builder("logs.by.source")
                .description("Logs by source")
                .tag("application", "log-collector")
                .tag("source", source)
                .register(meterRegistry)
                .increment();

        Counter.builder("logs.by.environment")
                .description("Logs by environment")
                .tag("application", "log-collector")
                .tag("environment", environment)
                .register(meterRegistry)
                .increment();

        totalCounters.computeIfAbsent("level_" + level, k -> new AtomicLong(0))
                .incrementAndGet();
        totalCounters.computeIfAbsent("source_" + source, k -> new AtomicLong(0))
                .incrementAndGet();

        log.debug("📊 Recorded log: level={}, source={}, environment={}", level, source, environment);
    }

    /**
     * Записывает метрики ошибок ETL
     */
    public void recordEtlError(String errorType, String format, String source) {
        meterRegistry.counter("logs.parsing.errors.total", "type", errorType).increment();

        // Детальные ошибки по форматам
        meterRegistry.counter("etl.errors.by.format",
                "format", format,
                "type", errorType).increment();

        // Ошибки по источникам
        meterRegistry.counter("etl.errors.by.source",
                "source", source,
                "type", errorType).increment();

        // Агрегация ошибок в окнах
        for (SlidingWindowCounter counter : windowCounters.values()) {
            if (counter.getName().startsWith("errors")) {
                counter.add(1);
            }
        }

        // Обновляем счетчики ошибок
        errorCounters.computeIfAbsent(errorType, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Записывает метрику дубликатов
     */
    public void recordDuplicate(String source, String level) {
        meterRegistry.counter("etl.duplicates",
                "source", source,
                "level", level).increment();

        errorCounters.computeIfAbsent("duplicate", k -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Записывает метрику размера сообщения
     */
    public void recordMessageSize(int bytes, String format) {
        DistributionSummary.builder("logs.message.size")
                .description("Message size in bytes")
                .tags("format", format)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(bytes);
    }

    /**
     * Записывает метрику задержки доставки
     */
    public void recordDeliveryLag(long lagMs, String source) {
        meterRegistry.timer("logs.delivery.lag", "source", source)
                .record(Duration.ofMillis(lagMs));
    }

    /**
     * Получить текущую скорость обработки (логов/сек)
     */
    public double getCurrentRate() {
        SlidingWindowCounter counter = windowCounters.get("ingestion_60");
        return counter != null ? counter.getRate() : 0.0;
    }

    /**
     * Получить агрегированные метрики для мониторинга
     */
    public Map<String, Object> getAggregatedMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Общая статистика
        metrics.put("total_logs", getTotalLogs());
        metrics.put("total_errors", getTotalErrors());
        metrics.put("error_rate", calculateErrorRate());

        // Текущие rates
        Map<String, Double> rates = new HashMap<>();
        for (long window : windows) {
            SlidingWindowCounter counter = windowCounters.get("ingestion_" + window);
            if (counter != null) {
                rates.put(window + "s_rate", counter.getRate());
            }
        }
        metrics.put("rates", rates);

        // Статистика по уровням
        Map<String, Long> byLevel = new HashMap<>();
        totalCounters.forEach((key, value) -> {
            if (key.startsWith("level_")) {
                byLevel.put(key.substring(6), value.get());
            }
        });
        metrics.put("by_level", byLevel);

        // Статистика ошибок
        Map<String, Long> byError = new HashMap<>();
        errorCounters.forEach((key, value) -> byError.put(key, value.get()));
        metrics.put("by_error", byError);

        return metrics;
    }

    private long getTotalLogs() {
        return totalCounters.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    private long getTotalErrors() {
        return errorCounters.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    private double calculateErrorRate() {
        long total = getTotalLogs();
        if (total == 0) return 0.0;
        return (double) getTotalErrors() / total * 100;
    }

    /**
     * Скользящее окно для агрегации метрик
     */
    private static class SlidingWindowCounter {
        private final long windowSeconds;
        private final long[] buckets;
        private final long[] timestamps;
        private final String name;
        private int currentBucket = 0;

        public SlidingWindowCounter(long windowSeconds) {
            this.windowSeconds = windowSeconds;
            this.name = "ingestion_" + windowSeconds;
            // Создаем 10 бакетов для более гладкой агрегации
            this.buckets = new long[10];
            this.timestamps = new long[10];
            long now = System.currentTimeMillis() / 1000;
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] = now - (windowSeconds * i / timestamps.length);
            }
        }

        public synchronized void add(long value) {
            long now = System.currentTimeMillis() / 1000;
            rotate(now);
            buckets[currentBucket] += value;
        }

        public synchronized double getRate() {
            long now = System.currentTimeMillis() / 1000;
            rotate(now);

            long total = 0;
            long oldestValid = now - windowSeconds;

            for (int i = 0; i < buckets.length; i++) {
                if (timestamps[i] >= oldestValid) {
                    total += buckets[i];
                }
            }

            return (double) total / windowSeconds;
        }

        public String getName() {
            return name;
        }

        private void rotate(long now) {
            long bucketDuration = windowSeconds / buckets.length;

            for (int i = 0; i < buckets.length; i++) {
                if (timestamps[i] < now - windowSeconds) {
                    buckets[i] = 0;
                    timestamps[i] = now - (windowSeconds * i / buckets.length);
                }
            }

            // Определяем текущий бакет
            currentBucket = (int)((now % windowSeconds) / bucketDuration);
            if (currentBucket >= buckets.length) currentBucket = buckets.length - 1;
        }
    }
}