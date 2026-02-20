package ru.khkhlv.logcollector.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис для имитации работающих приложений, которые пишут логи в Kafka.
 * Запускается в фоновом режиме и периодически генерирует тестовые логи.
 */
@Slf4j
@Service
@EnableScheduling
public class LogGeneratorService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    private final AtomicLong counter = new AtomicLong(0);

    @Value("${app.generator.enabled:false}")
    private boolean enabled;

    @Value("${app.generator.rate:10}")
    private int logsPerSecond;

    @Value("${app.generator.topic:logs_raw}")
    private String[] topics;

    @Value("${app.generator.sources:auth-service,payment-service,order-service,user-service}")
    private String[] sources;

    public LogGeneratorService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        log.info("LogGeneratorService инициализирован");
    }

    /**
     * Генерация логов с заданной частотой (logsPerSecond)
     */
    @Scheduled(fixedDelay = 1000) // Проверяем каждую секунду, сколько логов нужно сгенерировать
    public void generateLogs() {
        if (!enabled) {
            return;
        }

        // Генерируем logsPerSecond логов в секунду (распределяем равномерно)
        int logsToGenerate = logsPerSecond / 10; // т.к. вызываемся 10 раз в секунду
        if (logsToGenerate < 1) logsToGenerate = 1;

        for (int i = 0; i < logsToGenerate; i++) {
            sendLog();
        }
    }

    /**
     * Отправка одного лога в случайный топик
     */
    private void sendLog() {
        try {
            String topic = topics[random.nextInt(topics.length)];
            String logMessage = generateLogMessage();

            kafkaTemplate.send(topic, logMessage)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            long count = counter.incrementAndGet();
                            if (count % 100 == 0) {
                                log.info("Отправлено {} логов в Kafka", count);
                            }
                        } else {
                            log.error("Ошибка отправки лога в Kafka", ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Ошибка при генерации лога", e);
        }
    }

    /**
     * Генерация случайного лога
     */
    private String generateLogMessage() throws Exception {
        Map<String, Object> logEntry = new HashMap<>();

        // Основные поля
        logEntry.put("id", UUID.randomUUID().toString());
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("level", getRandomLevel());
        logEntry.put("source", sources[random.nextInt(sources.length)]);
        logEntry.put("host", "host-" + (random.nextInt(5) + 1));
        logEntry.put("environment", random.nextBoolean() ? "production" : "staging");

        // Сообщение
        logEntry.put("message", generateRandomMessage());

        // Payload с дополнительной информацией
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", random.nextInt(10000));
        payload.put("requestId", UUID.randomUUID().toString());
        payload.put("duration", random.nextInt(500));
        payload.put("statusCode", random.nextInt(500));

        // Для ошибок добавляем stack trace
        if ("ERROR".equals(logEntry.get("level"))) {
            payload.put("errorType", getRandomErrorType());
            payload.put("stackTrace", "java.lang.NullPointerException: null\n\tat com.example.Service.method(Service.java:123)");
        }

        logEntry.put("payload", payload);

        return objectMapper.writeValueAsString(logEntry);
    }

    private String getRandomLevel() {
        String[] levels = {"INFO", "DEBUG", "WARN", "ERROR"};
        // Распределение: 70% INFO, 15% DEBUG, 10% WARN, 5% ERROR
        int r = random.nextInt(100);
        if (r < 70) return "INFO";
        if (r < 85) return "DEBUG";
        if (r < 95) return "WARN";
        return "ERROR";
    }

    private String getRandomErrorType() {
        String[] errors = {
                "NullPointerException",
                "IllegalArgumentException",
                "RuntimeException",
                "IOException",
                "SQLException",
                "TimeoutException"
        };
        return errors[random.nextInt(errors.length)];
    }

    private String generateRandomMessage() {
        String[] templates = {
                "User {} logged in from IP {}",
                "Processing payment for order {}",
                "Database query executed in {} ms",
                "Cache miss for key {}",
                "Sending email to user {}",
                "Validation failed for field {}",
                "Service {} is {}",
                "Request processed in {} ms"
        };

        String template = templates[random.nextInt(templates.length)];
        return template.replace("{}", String.valueOf(random.nextInt(1000)));
    }

    /**
     * Запуск генерации по требованию
     */
    @Async
    public void generateBulk(int count) {
        log.info("Генерация {} логов по требованию", count);
        for (int i = 0; i < count; i++) {
            sendLog();
            if (i % 100 == 0) {
                try {
                    Thread.sleep(10); // Небольшая пауза чтобы не перегрузить Kafka
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("Генерация {} логов завершена", count);
    }

    /**
     * Получение статистики
     */
    public long getTotalGenerated() {
        return counter.get();
    }

    /**
     * Включение/выключение генератора
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("Генератор логов {}", enabled ? "включен" : "выключен");
    }
}