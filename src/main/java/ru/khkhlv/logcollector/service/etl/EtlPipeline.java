package ru.khkhlv.logcollector.service.etl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class EtlPipeline {

    private final EtlTask extractTask;
    private final EtlTask transformTask;
    private final EtlTask loadTask;

    public EtlPipeline(EtlTask extractTask, EtlTask transformTask, EtlTask loadTask) {
        this.extractTask = extractTask;
        this.transformTask = transformTask;
        this.loadTask = loadTask;
    }

    public void execute(String sourceType, Instant from, Instant to) {
        log.info("Starting ETL pipeline: source={}, from={}, to={}", sourceType, from, to);

        // Extract
        List<String> rawLogs = extractTask.execute(sourceType, from, to);
        log.info("Extracted {} raw logs", rawLogs.size());

        // Transform
        List<EtlTask.TransformedLog> transformed = transformTask.transform(rawLogs);
        log.info("Transformed {} logs", transformed.size());

        // Load
        int loaded = loadTask.load(transformed);
        log.info("Loaded {} logs to warehouse", loaded);

        log.info("ETL pipeline completed successfully");
    }
}
