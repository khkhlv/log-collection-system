package ru.khkhlv.logcollector.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.khkhlv.logcollector.model.LogEntry;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ClfLogParser implements LogParser {

    // Apache CLF: %h %l %u %t "%r" %>s %b
    private static final Pattern CLF_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+\\[([^\\]]+)\\]\\s+\"([^\"]+)\"\\s+(\\d+)\\s+(\\S+)"
    );

    private static final DateTimeFormatter CLF_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    @Override
    public LogEntry parse(String rawLog) {
        Matcher matcher = CLF_PATTERN.matcher(rawLog.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid CLF format");
        }

        String host = matcher.group(1);
        String timestamp = matcher.group(4);
        String request = matcher.group(5);
        int statusCode = Integer.parseInt(matcher.group(6));
        String bytes = matcher.group(7);

        // Парсинг запроса
        String[] requestParts = request.split(" ");
        String method = requestParts.length > 0 ? requestParts[0] : "";
        String path = requestParts.length > 1 ? requestParts[1] : "";

        // Определение уровня по статус-коду
        String level = determineLevel(statusCode);

        Map<String, Object> payload = new HashMap<>();
        payload.put("http_method", method);
        payload.put("http_path", path);
        payload.put("http_status_code", statusCode);
        payload.put("response_bytes", "-".equals(bytes) ? null : Integer.parseInt(bytes));

        return LogEntry.builder()
                .createdAt(parseTimestamp(timestamp))
                .level(level)
                .source("http-access")
                .host(host)
                .message(request)
                .payload(payload)
                .receivedAt(Instant.now())
                .formatType("clf")
                .build();
    }

    @Override
    public boolean canParse(String rawLog) {
        return CLF_PATTERN.matcher(rawLog.trim()).matches();
    }

    @Override
    public String getFormatName() {
        return "clf";
    }

    private String determineLevel(int statusCode) {
        if (statusCode >= 500) return "ERROR";
        if (statusCode >= 400) return "WARNING";
        if (statusCode >= 300) return "INFO";
        return "DEBUG";
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(timestamp, CLF_TIME_FORMAT);
            return zdt.toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse CLF timestamp: {}", timestamp);
            return Instant.now();
        }
    }
}