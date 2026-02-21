package ru.khkhlv.logcollector.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogMetricsCollectorTest {

    @Autowired
    private MeterRegistry meterRegistry;
    private LogMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new LogMetricsCollector(meterRegistry);
    }

    @Test
    void recordIngestion_IncrementsCounters() {
        // when
        metricsCollector.recordIngestion(5, 100);

        // then
        Counter totalLogsCounter = meterRegistry.counter("logs.ingested.total",
                "application", "log-collector", "type", "all");
        assertEquals(5.0, totalLogsCounter.count());

    }

    @Test
    void recordLogByLevel_IncrementsLevelCounters() {
        // when
        metricsCollector.recordLogByLevel("ERROR", "test-service", "production");

        // then - проверяем через MeterRegistry с правильными тегами
        Counter errorCounter = meterRegistry.counter("logs.by.level",
                "application", "log-collector", "level", "ERROR");
        assertEquals(1.0, errorCounter.count());

        Counter sourceCounter = meterRegistry.counter("logs.by.source",
                "application", "log-collector", "source", "test-service");
        assertEquals(1.0, sourceCounter.count());

        Counter envCounter = meterRegistry.counter("logs.by.environment",
                "application", "log-collector", "environment", "production");
        assertEquals(1.0, envCounter.count());

        // Проверяем через агрегированные метрики
        Map<String, Object> aggregated = metricsCollector.getAggregatedMetrics();
        Map<String, Long> byLevel = (Map<String, Long>) aggregated.get("by_level");
        assertEquals(1L, byLevel.get("ERROR"));
    }


    @Test
    void recordEtlError_IncrementsErrorCounters() {
        // when
        metricsCollector.recordEtlError("validation", "json", "test-source");

        // then
        double errors = meterRegistry.counter("logs.parsing.errors.total", "type", "validation").count();
        assertEquals(1.0, errors);

        double formatErrors = meterRegistry.counter("etl.errors.by.format",
                "format", "json", "type", "validation").count();
        assertEquals(1.0, formatErrors);
    }

    @Test
    void getCurrentRate_ReturnsPositiveRate() {
        // given
        metricsCollector.recordIngestion(100, 1000);

        // when
        double rate = metricsCollector.getCurrentRate();

        // then
        assertTrue(rate >= 0);
    }

    @Test
    void getAggregatedMetrics_ReturnsNonEmptyMap() {
        // given
        metricsCollector.recordIngestion(10, 100);
        metricsCollector.recordLogByLevel("INFO", "test", "prod");
        metricsCollector.recordEtlError("validation", "json", "test");

        // when
        Map<String, Object> metrics = metricsCollector.getAggregatedMetrics();

        // then
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("total_logs"));
        assertTrue(metrics.containsKey("total_errors"));
        assertTrue(metrics.containsKey("error_rate"));
        assertTrue(metrics.containsKey("by_level"));
        assertTrue(metrics.containsKey("by_error"));
        assertTrue(metrics.containsKey("rates"));
    }
}