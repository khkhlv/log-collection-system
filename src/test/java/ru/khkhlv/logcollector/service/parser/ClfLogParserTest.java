package ru.khkhlv.logcollector.service.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.khkhlv.logcollector.model.LogEntry;

import static org.junit.jupiter.api.Assertions.*;

class ClfLogParserTest {

    private ClfLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new ClfLogParser();
    }

    @Test
    void parse_ValidClfLog_ReturnsLogEntry() {
        // given
        String clfLog = "192.168.1.1 - - [15/Jan/2024:10:30:00 +0000] \"GET /api/users HTTP/1.1\" 200 1234";

        // when
        LogEntry result = parser.parse(clfLog);

        // then
        assertNotNull(result);
        assertEquals("http-access", result.getSource());
        assertEquals("192.168.1.1", result.getHost());
        assertEquals("GET /api/users HTTP/1.1", result.getMessage());
        assertEquals("clf", result.getFormatType());

        assertNotNull(result.getPayload());
        assertEquals("GET", result.getPayload().get("http_method"));
        assertEquals("/api/users", result.getPayload().get("http_path"));
        assertEquals(200, result.getPayload().get("http_status_code"));
        assertEquals(1234, result.getPayload().get("response_bytes"));
    }

    @ParameterizedTest
    @CsvSource({
            "200, DEBUG",
            "301, INFO",
            "404, WARNING",
            "500, ERROR"
    })
    void parse_DifferentStatusCodes_ReturnsCorrectLevel(int statusCode, String expectedLevel) {
        // given
        String clfLog = String.format(
                "192.168.1.1 - - [15/Jan/2024:10:30:00 +0000] \"GET /test HTTP/1.1\" %d 1234",
                statusCode
        );

        // when
        LogEntry result = parser.parse(clfLog);

        // then
        assertEquals(expectedLevel, result.getLevel());
    }

    @Test
    void parse_InvalidClfFormat_ThrowsException() {
        // given
        String invalidLog = "invalid log format";

        // when & then
        assertThrows(IllegalArgumentException.class, () -> parser.parse(invalidLog));
    }

    @Test
    void canParse_ValidClfLog_ReturnsTrue() {
        // given
        String validLog = "192.168.1.1 - - [15/Jan/2024:10:30:00 +0000] \"GET /test HTTP/1.1\" 200 1234";

        // when
        boolean result = parser.canParse(validLog);

        // then
        assertTrue(result);
    }
}
