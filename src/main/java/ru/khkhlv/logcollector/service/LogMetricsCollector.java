package ru.khkhlv.logcollector.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogMetricsCollector {

    private final MeterRegistry meterRegistry;

    /**
     * Записывает метрики приёма логов через HTTP/файлы/Kafka.
     *
     * @param count      количество сохранённых логов
     * @param durationMs длительность обработки батча в миллисекундах
     */
    public void recordIngestion(int count, long durationMs) {
        Counter.builder("logs.ingested.total")
                .description("Total number of ingested logs")
                .register(meterRegistry)
                .increment(count);

        DistributionSummary.builder("logs.ingestion.batch.duration.ms")
                .description("Batch ingestion duration in milliseconds")
                .register(meterRegistry)
                .record(durationMs);

        DistributionSummary.builder("logs.ingestion.batch.size")
                .description("Batch size of ingested logs")
                .register(meterRegistry)
                .record(count);
    }

    /**
     * Количество ошибок парсинга по форматам.
     */
    public void recordParsingError(String format) {
        Counter.builder("logs.parsing.errors.total")
                .description("Total number of parsing errors")
                .tag("format", format)
                .register(meterRegistry)
                .increment();
    }
}

