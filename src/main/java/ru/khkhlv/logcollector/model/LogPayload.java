package ru.khkhlv.logcollector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogPayload {
    private Long userId;
    private Integer durationMs;
    private Integer httpStatusCode;
    private String errorType;
    private String stackTrace;
    private String requestId;
    private String sessionId;
}
