package ru.khkhlv.logcollector.service.etl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class EtlSchedulerService {

    private final EtlPipeline etlPipeline;

    @Value("${etl.source-type:kafka}")
    private String sourceType;

    @Value("${etl.interval-hours:1}")
    private int intervalHours;

    public EtlSchedulerService(EtlPipeline etlPipeline) {
        this.etlPipeline = etlPipeline;
    }

    /**
     * Запуск ETL каждый час (по умолчанию)
     */
    @Scheduled(cron = "${etl.schedule:0 0 * * * *}") // Каждый час
    public void runScheduledEtl() {
        log.info("Запуск планового ETL процесса");

        Instant to = Instant.now();
        Instant from = to.minus(java.time.Duration.ofHours(intervalHours));

        try {
            etlPipeline.execute(sourceType, from, to);
        } catch (Exception e) {
            log.error("Ошибка при выполнении ETL", e);
        }
    }

    /**
     * Ручной запуск ETL за последние N часов
     */
    public void runManualEtl(int hours) {
        Instant to = Instant.now();
        Instant from = to.minus(java.time.Duration.ofHours(hours));

        log.info("Ручной запуск ETL за последние {} часов", hours);
        etlPipeline.execute(sourceType, from, to);
    }

    /**
     * Ручной запуск ETL за конкретный период
     */
    public void runManualEtl(Instant from, Instant to) {
        log.info("Ручной запуск ETL за период: {} - {}", from, to);
        etlPipeline.execute(sourceType, from, to);
    }
}