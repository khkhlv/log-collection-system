package ru.khkhlv.logcollector.service.ingestion;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.khkhlv.logcollector.service.etl.EtlService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private EtlService etlService;

    private KafkaConsumerService kafkaConsumerService;

    @BeforeEach
    void setUp() {
        kafkaConsumerService = new KafkaConsumerService(etlService);
    }

    @Test
    void consumeLog_ValidRecord_ProcessesThroughEtl() {
        // given
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("log-format", "json".getBytes()));
        headers.add(new RecordHeader("source", "kafka-test".getBytes()));

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "logs-topic", 0, 100L, 0L, TimestampType.CREATE_TIME,
                0L, 0, 0, "key", "{\"message\":\"test\"}", headers
        );

        // when
        kafkaConsumerService.consumeLog(record);

        // then
        verify(etlService).processLog(
                eq("{\"message\":\"test\"}"),
                eq("json"),
                eq("kafka-test")
        );
    }

    @Test
    void consumeLog_WithoutHeaders_UsesDefaults() {
        // given
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "logs-topic", 0, 100L, "key", "{\"message\":\"test\"}"
        );

        // when
        kafkaConsumerService.consumeLog(record);

        // then
        verify(etlService).processLog(
                eq("{\"message\":\"test\"}"),
                eq("json"),
                eq("kafka")
        );
    }

    @Test
    void consumeLog_EtlThrowsException_LogsError() {
        // given
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "logs-topic", 0, 100L, "key", "invalid json"
        );

        doThrow(new RuntimeException("ETL error"))
                .when(etlService).processLog(anyString(), anyString(), anyString());

        // when
        kafkaConsumerService.consumeLog(record);

        // then
        verify(etlService).processLog(anyString(), anyString(), anyString());
        // No exception is thrown to Kafka
    }
}
