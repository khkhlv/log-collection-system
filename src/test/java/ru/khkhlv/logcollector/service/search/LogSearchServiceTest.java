package ru.khkhlv.logcollector.service.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.khkhlv.logcollector.controller.DashboardStats;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.repository.LogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogSearchServiceTest {

    @Mock
    private LogRepository logRepository;

    private LogSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new LogSearchService(logRepository);
    }

    @Test
    void search_WithAllParameters_ReturnsPageOfLogs() {
        // given
        String message = "error";
        String level = "ERROR";
        String source = "test-service";
        String host = "server-01";
        String environment = "production";
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        Map<String, String> payload = Map.of("user_id", "123");
        int page = 0;
        int size = 10;

        List<LogEntry> expectedLogs = List.of(
                LogEntry.builder().id(1L).level("ERROR").message("test error").build()
        );
        Page<LogEntry> expectedPage = new PageImpl<>(expectedLogs);

        when(logRepository.searchLogs(
                eq(message), eq(level), eq(source), eq(host), eq(environment),
                eq(from), eq(to), anyMap(), any(Pageable.class)
        )).thenReturn(expectedPage);

        // when
        Page<LogEntry> result = searchService.search(
                message, level, source, host, environment, from, to, payload, page, size
        );

        // then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(logRepository).searchLogs(
                eq(message), eq(level), eq(source), eq(host), eq(environment),
                eq(from), eq(to), anyMap(), any(Pageable.class)
        );
    }

    @Test
    void getDashboardStats_ReturnsAggregatedStats() {
        // given
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        List<Object[]> byLevel = List.of(
                new Object[]{"ERROR", 10L},
                new Object[]{"INFO", 100L},
                new Object[]{"WARN", 5L}
        );

        List<Object[]> bySource = List.of(
                new Object[]{"service-a", 50L},
                new Object[]{"service-b", 30L},
                new Object[]{"service-c", 20L}
        );

        List<Object[]> errorTypes = List.of(
                new Object[]{"NullPointerException", 5L},
                new Object[]{"IOException", 3L},
                new Object[]{"TimeoutException", 2L}
        );

        List<String> deadSources = List.of("service-d", "service-e");

        when(logRepository.countByCriteria(null, null, from, to)).thenReturn(115L);
        when(logRepository.countLogsByLevel(from, to)).thenReturn(byLevel);
        when(logRepository.countLogsBySource(from, to)).thenReturn(bySource);
        when(logRepository.topErrorTypes(from, to)).thenReturn(errorTypes);
        when(logRepository.findDeadSources(any(Instant.class))).thenReturn(deadSources);

        // when
        DashboardStats stats = searchService.getDashboardStats(from, to);

        // then
        assertNotNull(stats);
        assertEquals(115L, stats.getTotalLogs());
        assertEquals(3, stats.getLogsByLevel().size());
        assertEquals(3, stats.getTopSources().size());
        assertEquals(3, stats.getTopErrorTypes().size());
        assertEquals(2, stats.getDeadSources().size());

        assertEquals(100L, stats.getLogsByLevel().get("INFO"));
        assertEquals(10L, stats.getLogsByLevel().get("ERROR"));

        assertEquals("service-a", stats.getTopSources().get(0).getKey());
        assertEquals(50L, stats.getTopSources().get(0).getCount());
    }

    @Test
    void findById_ExistingId_ReturnsLogEntry() {
        // given
        Long id = 1L;
        LogEntry expected = LogEntry.builder().id(id).level("INFO").build();

        when(logRepository.findById(id)).thenReturn(java.util.Optional.of(expected));

        // when
        var result = searchService.findById(id);

        // then
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        assertEquals("INFO", result.get().getLevel());
    }

    @Test
    void findById_NonExistingId_ReturnsEmpty() {
        // given
        Long id = 999L;
        when(logRepository.findById(id)).thenReturn(java.util.Optional.empty());

        // when
        var result = searchService.findById(id);

        // then
        assertTrue(result.isEmpty());
    }
}
