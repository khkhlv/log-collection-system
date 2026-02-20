package ru.khkhlv.logcollector.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.khkhlv.logcollector.model.LogEntry;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class SyslogParser implements LogParser {

    // RFC5424 pattern (упрощённый)
    private static final Pattern SYSLOG_PATTERN = Pattern.compile(
            "^<(\\d+)>(\\d+)\\s+([^\\s]+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)(?:\\s+(.*))?$"
    );

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public LogEntry parse(String rawLog) {
        Matcher matcher = SYSLOG_PATTERN.matcher(rawLog.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid syslog format");
        }

        int priority = Integer.parseInt(matcher.group(1));
        String timestamp = matcher.group(3);
        String hostname = matcher.group(4);
        String appname = matcher.group(5);
        String procId = matcher.group(6);
        String msgId = matcher.group(7);
        String message = matcher.group(8) != null ? matcher.group(8) : "";

        // Извлечение уровня из priority
        int severity = priority & 0x07;
        String level = mapSeverity(severity);

        return LogEntry.builder()
                .createdAt(parseTimestamp(timestamp))
                .level(level)
                .source(appname)
                .host(hostname)
                .message(message)
                .receivedAt(Instant.now())
                .formatType("syslog")
                .build();
    }

    @Override
    public boolean canParse(String rawLog) {
        return rawLog.trim().startsWith("<") && SYSLOG_PATTERN.matcher(rawLog.trim()).matches();
    }

    @Override
    public String getFormatName() {
        return "syslog";
    }

    private String mapSeverity(int severity) {
        return switch (severity) {
            case 0 -> "EMERGENCY";
            case 1 -> "ALERT";
            case 2 -> "CRITICAL";
            case 3 -> "ERROR";
            case 4 -> "WARNING";
            case 5 -> "NOTICE";
            case 6 -> "INFO";
            case 7 -> "DEBUG";
            default -> "INFO";
        };
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.from(TIMESTAMP_FORMATTER.parse(timestamp));
        } catch (Exception e) {
            log.warn("Failed to parse timestamp: {}", timestamp);
            return Instant.now();
        }
    }
}