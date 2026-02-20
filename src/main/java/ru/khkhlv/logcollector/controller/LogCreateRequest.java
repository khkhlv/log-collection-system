package ru.khkhlv.logcollector.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogCreateRequest {

    @NotNull
    @JsonProperty("created_at")
    private Instant createdAt;

    @NotBlank
    private String level;

    @NotBlank
    private String source;

    private String host;

    private String environment;

    @NotBlank
    private String message;

    /**
     * Произвольные дополнительные поля.
     * Ожидается структура как в примере:
     * {
     *   "user_id": 1001,
     *   "http_status_code": 504,
     *   ...
     * }
     */
    private Map<String, Object> payload;

    // Явные геттеры для совместимости
    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLevel() {
        return level;
    }

    public String getSource() {
        return source;
    }

    public String getHost() {
        return host;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}

