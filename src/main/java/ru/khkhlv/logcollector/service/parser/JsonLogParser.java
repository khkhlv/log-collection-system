package ru.khkhlv.logcollector.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.khkhlv.logcollector.model.LogEntry;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JsonLogParser implements LogParser {

    private final ObjectMapper mapper;

    public JsonLogParser() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public LogEntry parse(String rawLog) {
        try {
            JsonNode node = mapper.readTree(rawLog);

            return LogEntry.builder()
                    .createdAt(parseTimestamp(node))
                    .level(parseLevel(node))
                    .source(node.path("source").asText())
                    .host(node.path("host").asText())
                    .environment(node.path("environment").asText())
                    .message(node.path("message").asText())
                    .payload(extractPayload(node))
                    .receivedAt(Instant.now())
                    .formatType("json")
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse JSON log", e);
            throw new RuntimeException("JSON parsing failed", e);
        }
    }

    @Override
    public boolean canParse(String rawLog) {
        return rawLog.trim().startsWith("{") || rawLog.trim().startsWith("[");
    }

    @Override
    public String getFormatName() {
        return "json";
    }

    private Instant parseTimestamp(JsonNode node) {
        if (node.has("created_at")) {
            return Instant.parse(node.path("created_at").asText());
        }
        if (node.has("timestamp")) {
            return Instant.parse(node.path("timestamp").asText());
        }
        if (node.has("@timestamp")) {
            return Instant.parse(node.path("@timestamp").asText());
        }
        return Instant.now();
    }

    private String parseLevel(JsonNode node) {
        if (node.has("level")) {
            return node.path("level").asText().toUpperCase();
        }
        if (node.has("severity")) {
            return node.path("severity").asText().toUpperCase();
        }
        return "INFO";
    }

    private Map<String, Object> extractPayload(JsonNode node) {
        Map<String, Object> payload = new HashMap<>();

        if (node.has("payload") && node.path("payload").isObject()) {
            JsonNode payloadNode = node.path("payload");
            payloadNode.fields().forEachRemaining(entry ->
                    payload.put(entry.getKey(), entry.getValue().asText())
            );
        }

        // Добавляем дополнительные поля если они есть
        if (node.has("user_id")) payload.put("user_id", node.path("user_id").asLong());
        if (node.has("duration_ms")) payload.put("duration_ms", node.path("duration_ms").asInt());
        if (node.has("http_status_code")) payload.put("http_status_code", node.path("http_status_code").asInt());
        if (node.has("error_type")) payload.put("error_type", node.path("error_type").asText());
        if (node.has("stack_trace")) payload.put("stack_trace", node.path("stack_trace").asText());

        return payload.isEmpty() ? null : payload;
    }
}
