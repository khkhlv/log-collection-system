package ru.khkhlv.logcollector.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.khkhlv.logcollector.model.LogEntry;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JsonLogParserTest {

    private JsonLogParser parser;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        parser = new JsonLogParser();
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void parse_ValidJsonLog_ReturnsLogEntry() {
        // given
        String jsonLog = """
                {
                    "created_at": "2024-01-15T10:30:00Z",
                    "level": "ERROR",
                    "source": "test-service",
                    "host": "server-01",
                    "environment": "production",
                    "message": "Test error message",
                    "user_id": 12345,
                    "duration_ms": 1500
                }
                """;

        // when
        LogEntry result = parser.parse(jsonLog);

        // then
        assertNotNull(result);
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), result.getCreatedAt());
        assertEquals("ERROR", result.getLevel());
        assertEquals("test-service", result.getSource());
        assertEquals("server-01", result.getHost());
        assertEquals("production", result.getEnvironment());
        assertEquals("Test error message", result.getMessage());
        assertEquals("json", result.getFormatType());

        Map<String, Object> payload = result.getPayload();
        assertNotNull(payload);
        assertEquals(12345L, payload.get("user_id"));
        assertEquals(1500, payload.get("duration_ms"));
    }

    @Test
    void parse_JsonWithAlternativeFields_ReturnsLogEntry() {
        // given
        String jsonLog = """
                {
                    "@timestamp": "2024-01-15T10:30:00Z",
                    "severity": "WARNING",
                    "source": "api-gateway",
                    "message": "Slow response",
                    "http_status_code": 504
                }
                """;

        // when
        LogEntry result = parser.parse(jsonLog);

        // then
        assertNotNull(result);
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), result.getCreatedAt());
        assertEquals("WARNING", result.getLevel());
        assertEquals("api-gateway", result.getSource());
        assertEquals("Slow response", result.getMessage());
        assertEquals(504, result.getPayload().get("http_status_code"));
    }

    @Test
    void parse_InvalidJson_ThrowsException() {
        // given
        String invalidJson = "{ invalid json }";

        // when & then
        assertThrows(RuntimeException.class, () -> parser.parse(invalidJson));
    }

    @Test
    void canParse_ValidJson_ReturnsTrue() {
        // given
        String jsonLog = "{\"message\":\"test\"}";

        // when
        boolean result = parser.canParse(jsonLog);

        // then
        assertTrue(result);
    }

    @Test
    void canParse_InvalidJson_ReturnsFalse() {
        // given
        String notJson = "plain text log message";

        // when
        boolean result = parser.canParse(notJson);

        // then
        assertFalse(result);
    }
}