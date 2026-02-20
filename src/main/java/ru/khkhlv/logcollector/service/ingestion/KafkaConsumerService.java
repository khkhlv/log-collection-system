package ru.khkhlv.logcollector.service.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.service.LogService;

@Service
@Slf4j
public class KafkaConsumerService {

    private final LogService logService;

    public KafkaConsumerService(LogService logService) {
        this.logService = logService;
    }

    @KafkaListener(topics = "${app.ingestion.kafka.topics}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeLog(ConsumerRecord<String, String> record) {
        try {
            String rawLog = record.value();
            String format = record.headers()
                    .lastHeader("log-format") != null
                    ? new String(record.headers().lastHeader("log-format").value())
                    : "json";

            LogEntry entry = logService.ingestRawLog(rawLog, format, "kafka");
            log.debug("Processed log from Kafka: id={}", entry.getId());

        } catch (Exception e) {
            log.error("Failed to process Kafka message", e);
            // Здесь можно отправить в DLQ
        }
    }
}