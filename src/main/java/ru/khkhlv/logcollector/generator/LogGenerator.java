package ru.khkhlv.logcollector.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Генератор тестовых логов в разных форматах.
 * Поддерживает генерацию JSON, CLF и Syslog форматов.
 * Может отправлять логи через HTTP, файлы и Kafka.
 */
@Component
public class LogGenerator {

    private static final Logger log = LoggerFactory.getLogger(LogGenerator.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.generator.http.url:http://localhost:8080/api/v1/logs}")
    private String httpUrl;

    @Value("${app.generator.kafka.topic:logs_raw}")
    private String kafkaTopics;

    @Value("${app.generator.file.output-dir:./logs}")
    private String outputDir;

    // Источники логов
    private static final String[] SOURCES = {
            "payment-service", "user-service", "order-service", "auth-service",
            "notification-service", "analytics-service", "api-gateway", "frontend-app",
            "backend-api", "database-service", "cache-service", "search-service"
    };

    // Уровни логов
    private static final String[] LEVELS = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"};

    // Окружения
    private static final String[] ENVIRONMENTS = {"dev", "staging", "production"};

    // Хосты
    private static final String[] HOSTS = {
            "prod-server-01.com", "prod-server-02.com", "prod-server-03.com",
            "staging-server-01.com", "dev-server-01.com", "dev-server-02.com"
    };

    // Типы ошибок
    private static final String[] ERROR_TYPES = {
            "TimeoutException", "NullPointerException", "IllegalArgumentException",
            "DatabaseConnectionException", "NetworkException", "AuthenticationException",
            "AuthorizationException", "ValidationException", "BusinessLogicException"
    };

    // HTTP методы для CLF
    private static final String[] HTTP_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH"};

    // Пути для CLF
    private static final String[] HTTP_PATHS = {
            "/api/users", "/api/orders", "/api/payments", "/api/products",
            "/api/auth/login", "/api/auth/logout", "/api/health", "/api/metrics"
    };

    // HTTP статус коды
    private static final int[] HTTP_STATUS_CODES = {200, 201, 400, 401, 403, 404, 500, 502, 503, 504};

    // Сообщения для разных уровней
    private static final Map<String, String[]> MESSAGES = Map.of(
            "DEBUG", new String[]{
                    "Processing request", "Cache hit", "Database query executed",
                    "Validation passed", "Starting transaction"
            },
            "INFO", new String[]{
                    "User logged in", "Order created", "Payment processed",
                    "Email sent", "File uploaded", "Data synchronized"
            },
            "WARNING", new String[]{
                    "Slow query detected", "Cache miss", "Retry attempt",
                    "Deprecated API used", "High memory usage"
            },
            "ERROR", new String[]{
                    "Payment processing failed", "Database connection lost",
                    "Authentication failed", "Invalid request format",
                    "External service unavailable"
            },
            "CRITICAL", new String[]{
                    "System outage detected", "Database corruption",
                    "Security breach attempt", "Service unavailable"
            }
    );

    /**
     * ✅ Единственный конструктор — Spring будет использовать его для DI.
     * Все зависимости обязательные, поэтому нет конструктора по умолчанию.
     */
    public LogGenerator(
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new JavaTimeModule());
        this.restTemplate = restTemplate;
        this.kafkaTemplate = kafkaTemplate;
        log.info("LogGenerator initialized with Kafka template: {}", kafkaTemplate != null);
    }

    /**
     * Генерирует указанное количество логов и отправляет их через указанный метод.
     */
    public void generateLogs(int count, String format, String method, int batchSize) {
        log.info("Generating {} logs in {} format, sending via {}", count, format, method);

        int generated = 0;
        List<String> batch = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String logLine = generateLogLine(format);
            batch.add(logLine);

            if (batch.size() >= batchSize) {
                sendBatch(batch, format, method);
                batch.clear();
                generated += batchSize;
                log.debug("Generated {} logs", generated);
            }
        }

        // Отправка оставшихся логов
        if (!batch.isEmpty()) {
            sendBatch(batch, format, method);
            generated += batch.size();
        }

        log.info("Successfully generated and sent {} logs", generated);
    }

    /**
     * Генерирует одну строку лога в указанном формате.
     */
    private String generateLogLine(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> generateJsonLog();
            case "clf" -> generateClfLog();
            case "syslog" -> generateSyslogLog();
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    /**
     * Генерирует JSON лог.
     */
    private String generateJsonLog() {
        try {
            String level = randomElement(LEVELS);
            String source = randomElement(SOURCES);
            String environment = randomElement(ENVIRONMENTS);
            String host = randomElement(HOSTS);
            String message = randomElement(MESSAGES.getOrDefault(level, MESSAGES.get("INFO")));

            Map<String, Object> logData = new HashMap<>();
            logData.put("created_at", Instant.now().toString());
            logData.put("level", level.toLowerCase());
            logData.put("source", source);
            logData.put("host", host);
            logData.put("environment", environment);
            logData.put("message", message);

            Map<String, Object> payload = new HashMap<>();
            if (ThreadLocalRandom.current().nextDouble() < 0.7) {
                payload.put("user_id", ThreadLocalRandom.current().nextLong(1000, 10000));
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                payload.put("duration_ms", ThreadLocalRandom.current().nextInt(10, 5000));
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                payload.put("http_status_code", randomElement(HTTP_STATUS_CODES));
            }
            if (level.equals("ERROR") || level.equals("CRITICAL")) {
                payload.put("error_type", randomElement(ERROR_TYPES));
                if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                    payload.put("stack_trace", generateStackTrace());
                }
            }
            if (!payload.isEmpty()) {
                logData.put("payload", payload);
            }

            return objectMapper.writeValueAsString(logData);
        } catch (Exception e) {
            log.error("Failed to generate JSON log", e);
            throw new RuntimeException("Failed to generate JSON log", e);
        }
    }

    /**
     * Генерирует CLF (Common Log Format) лог.
     */
    private String generateClfLog() {
        String host = randomElement(HOSTS).split("\\.")[0];
        String ident = "-";
        String user = ThreadLocalRandom.current().nextDouble() < 0.3
                ? "user" + ThreadLocalRandom.current().nextInt(1000)
                : "-";
        String method = randomElement(HTTP_METHODS);
        String path = randomElement(HTTP_PATHS);
        int statusCode = randomElement(HTTP_STATUS_CODES);
        int bytes = ThreadLocalRandom.current().nextInt(100, 10000);
        String httpVersion = "HTTP/1.1";

        ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

        return String.format("%s %s %s [%s] \"%s %s %s\" %d %d",
                host, ident, user, timestamp.format(formatter), method, path, httpVersion, statusCode, bytes);
    }

    /**
     * Генерирует Syslog лог (RFC5424 упрощённый).
     */
    private String generateSyslogLog() {
        String level = randomElement(LEVELS);
        int severity = mapLevelToSeverity(level);
        int facility = 16; // Local use 0
        int priority = (facility << 3) | severity;

        String timestamp = Instant.now().toString();
        String hostname = randomElement(HOSTS).split("\\.")[0];
        String appName = randomElement(SOURCES).replace("-service", "");
        String procId = String.valueOf(ThreadLocalRandom.current().nextInt(1000, 9999));
        String msgId = "MSG" + ThreadLocalRandom.current().nextInt(100, 999);
        String message = randomElement(MESSAGES.getOrDefault(level, MESSAGES.get("INFO")));

        return String.format("<%d>1 %s %s %s %s %s - %s",
                priority, timestamp, hostname, appName, procId, msgId, message);
    }

    /**
     * Отправляет батч логов через указанный метод.
     */
    private void sendBatch(List<String> batch, String format, String method) {
        try {
            switch (method.toLowerCase()) {
                case "http" -> sendViaHttp(batch, format);
                case "file" -> sendViaFile(batch, format);
                case "kafka" -> sendViaKafka(batch, format);
                default -> throw new IllegalArgumentException("Unsupported method: " + method);
            }
        } catch (Exception e) {
            log.error("Failed to send batch via {}", method, e);
            throw new RuntimeException("Batch send failed", e);
        }
    }

    /**
     * Отправляет логи через HTTP POST.
     */
    private void sendViaHttp(List<String> batch, String format) {
        if (!format.equals("json")) {
            log.warn("HTTP method only supports JSON format, skipping batch");
            return;
        }

        try {
            List<Map<String, Object>> requests = new ArrayList<>();
            for (String jsonLine : batch) {
                Map<String, Object> logData = objectMapper.readValue(jsonLine, Map.class);
                requests.add(logData);
            }

            restTemplate.postForObject(httpUrl, requests, Object.class);
            log.debug("Sent {} logs via HTTP", batch.size());
        } catch (Exception e) {
            log.error("Failed to send logs via HTTP", e);
            throw new RuntimeException("HTTP send failed", e);
        }
    }

    /**
     * Сохраняет логи в файл.
     */
    private void sendViaFile(List<String> batch, String format) {
        try {
            Path outputPath = Path.of(outputDir);
            Files.createDirectories(outputPath);

            String filename = String.format("logs-%s-%d.%s",
                    format, System.currentTimeMillis(), getFileExtension(format));
            Path filePath = outputPath.resolve(filename);

            Files.write(filePath, batch, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("Saved {} logs to file {}", batch.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to save logs to file", e);
            throw new RuntimeException("File save failed", e);
        }
    }

    /**
     * Отправляет логи через Kafka.
     */
    private void sendViaKafka(List<String> batch, String format) {
        // ✅ Теперь kafkaTemplate не будет null, если Kafka настроена
        if (kafkaTemplate == null) {
            log.error("KafkaTemplate is NULL - Kafka dependency not injected properly!");
            return;
        }

        try {
            String[] topics = kafkaTopics.split(",");
            String topic = topics[ThreadLocalRandom.current().nextInt(topics.length)].trim();

            for (String logLine : batch) {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, logLine);
                record.headers().add("log-format", format.getBytes());
                kafkaTemplate.send(record);
            }

            log.info("Sent {} logs via Kafka to topic {}", batch.size(), topic);
        } catch (Exception e) {
            log.error("Failed to send logs via Kafka", e);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    /**
     * Генерирует случайный стек-трейс для ошибок.
     */
    private String generateStackTrace() {
        return String.format("java.lang.%s: %s\n\tat com.example.Service.method(Service.java:123)\n\tat com.example.Controller.handle(Controller.java:45)",
                randomElement(ERROR_TYPES), "Error message");
    }

    /**
     * Маппит уровень лога в severity для Syslog.
     */
    private int mapLevelToSeverity(String level) {
        return switch (level) {
            case "DEBUG" -> 7;
            case "INFO" -> 6;
            case "WARNING" -> 4;
            case "ERROR" -> 3;
            case "CRITICAL" -> 2;
            default -> 6;
        };
    }

    /**
     * Возвращает расширение файла для формата.
     */
    private String getFileExtension(String format) {
        return switch (format.toLowerCase()) {
            case "json" -> "json";
            case "clf" -> "log";
            case "syslog" -> "log";
            default -> "txt";
        };
    }

    /**
     * Выбирает случайный элемент из массива.
     */
    @SafeVarargs
    private <T> T randomElement(T... elements) {
        return elements[ThreadLocalRandom.current().nextInt(elements.length)];
    }

    /**
     * Выбирает случайный элемент из массива int.
     */
    private int randomElement(int... elements) {
        return elements[ThreadLocalRandom.current().nextInt(elements.length)];
    }
}