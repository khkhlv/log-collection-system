package ru.khkhlv.logcollector.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.khkhlv.logcollector.service.etl.EtlService;

@Service
@Slf4j
public class KafkaConsumerService {

    private final EtlService etlService;

    public KafkaConsumerService(EtlService etlService) {
        this.etlService = etlService;
    }

    @KafkaListener(
            topics = "${app.ingestion.kafka.topics}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLog(ConsumerRecord<String, String> record) {
        try {
            // Извлекаем заголовки
            String format = record.headers().lastHeader("log-format") != null
                    ? new String(record.headers().lastHeader("log-format").value())
                    : "json";

            String source = record.headers().lastHeader("source") != null
                    ? new String(record.headers().lastHeader("source").value())
                    : "kafka";

            // Отправляем сразу в ETL для обработки
            etlService.processLog(record.value(), format, source);

            log.debug("Sent log to ETL pipeline: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());

        } catch (Exception e) {
            log.error("Failed to send Kafka message to ETL: topic={}, offset={}",
                    record.topic(), record.offset(), e);
        }
    }
}