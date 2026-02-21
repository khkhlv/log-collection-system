// file: src/test/java/ru/khkhlv/logcollector/service/ingestion/FileIngestionServiceTest.java
package ru.khkhlv.logcollector.service.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.khkhlv.logcollector.model.LogEntry;
import ru.khkhlv.logcollector.service.etl.EtlService;
import ru.khkhlv.logcollector.service.metrics.LogMetricsCollector;
import ru.khkhlv.logcollector.service.parser.LogParserFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileIngestionServiceTest {

    @Mock
    private EtlService etlService;

    @Mock
    private LogParserFactory parserFactory;

    @Mock
    private LogMetricsCollector metricsCollector;

    @Captor
    private ArgumentCaptor<List<String>> linesCaptor;

    @Captor
    private ArgumentCaptor<String> formatCaptor;

    @Captor
    private ArgumentCaptor<String> sourceCaptor;

    private FileIngestionService fileIngestionService;

    @TempDir
    Path tempDir;

    private LogEntry createTestLogEntry(String message) {
        return LogEntry.builder()
                .id(System.currentTimeMillis())
                .createdAt(Instant.now())
                .level("INFO")
                .source("test")
                .message(message)
                .receivedAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        fileIngestionService = new FileIngestionService(
                etlService, parserFactory, metricsCollector
        );

        // Устанавливаем корректные пути
        ReflectionTestUtils.setField(fileIngestionService, "watchDir", tempDir.toString());
        ReflectionTestUtils.setField(fileIngestionService, "processedDirName", "processed");
        ReflectionTestUtils.setField(fileIngestionService, "errorDirName", "error");
        ReflectionTestUtils.setField(fileIngestionService, "batchSize", 100);
    }

    @Test
    void pollForNewFiles_WithLogFile_ProcessesFile() throws IOException {
        // given
        Path logFile = tempDir.resolve("test.log");
        Files.writeString(logFile, """
            {"level":"INFO","source":"test","message":"line1"}
            {"level":"ERROR","source":"test","message":"line2"}
            """);

        List<LogEntry> processedEntries = new ArrayList<>();
        processedEntries.add(createTestLogEntry("line1"));
        processedEntries.add(createTestLogEntry("line2"));

        when(etlService.processLogs(anyList(), anyString(), anyString()))
                .thenReturn(processedEntries);

        // when
        fileIngestionService.pollForNewFiles();

        // then
        verify(etlService, times(1)).processLogs(
                linesCaptor.capture(),
                formatCaptor.capture(),
                sourceCaptor.capture()
        );

        assertEquals("json", formatCaptor.getValue());
        assertEquals("test", sourceCaptor.getValue());

        // Проверяем, что файл был перемещен
        Path processedDir = tempDir.resolve("processed");
        assertTrue(Files.exists(processedDir));

        // Должен быть хотя бы один файл в директории processed
        try (var files = Files.list(processedDir)) {
            assertTrue(files.findAny().isPresent());
        }

        // Исходный файл должен быть удален или перемещен
        assertFalse(Files.exists(logFile));
    }

    @Test
    void pollForNewFiles_WithMultipleLines_BatchesCorrectly() throws IOException {
        // given
        ReflectionTestUtils.setField(fileIngestionService, "batchSize", 2);

        Path logFile = tempDir.resolve("batch-test.log");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            content.append("{\"level\":\"INFO\",\"source\":\"test\",\"message\":\"line").append(i).append("\"}\n");
        }
        Files.writeString(logFile, content.toString());

        // Создаем реальные объекты LogEntry вместо моков
        when(etlService.processLogs(anyList(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> lines = invocation.getArgument(0);
                    List<LogEntry> result = new ArrayList<>();
                    for (String line : lines) {
                        result.add(createTestLogEntry(line));
                    }
                    return result;
                });

        // when
        fileIngestionService.pollForNewFiles();

        // then
        // Должно быть 3 вызова processLogs (2+2+1)
        verify(etlService, times(3)).processLogs(anyList(), anyString(), anyString());
    }



    @Test
    void pollForNewFiles_WhenWatchDirNotExists_SkipsProcessing() {
        // given
        String nonExistentDir = tempDir.resolve("non-existent").toString();
        ReflectionTestUtils.setField(fileIngestionService, "watchDir", nonExistentDir);

        // when
        fileIngestionService.pollForNewFiles();

        // then
        verify(etlService, never()).processLogs(anyList(), anyString(), anyString());
    }



    @Test
    void processSpecificFile_NonExistingFile_LogsWarning() {
        // given
        String nonExistentFile = tempDir.resolve("nonexistent.log").toString();

        // when
        fileIngestionService.processSpecificFile(nonExistentFile);

        // then
        verify(etlService, never()).processLogs(anyList(), anyString(), anyString());
    }

    @Test
    void processSpecificFile_WithProcessingError_HandlesException() throws IOException {
        // given
        Path logFile = tempDir.resolve("error-file.log");
        Files.writeString(logFile, "log line 1\nlog line 2");

        when(etlService.processLogs(anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Processing error"));

        // when - не должно выбрасывать исключение
        fileIngestionService.processSpecificFile(logFile.toString());

        // then
        verify(etlService).processLogs(anyList(), anyString(), anyString());

    }

    @Test
    void detectFormat_DifferentFileExtensions_ReturnsCorrectFormat() {
        // given
        String jsonFile = "test.json";
        String csvFile = "test.csv";
        String logFile = "test.log";
        String unknownFile = "test.xyz";

        // when & then - используем рефлексию для вызова приватного метода
        String jsonFormat = invokeDetectFormat(jsonFile, "{}");
        String csvFormat = invokeDetectFormat(csvFile, "col1,col2");
        String logFormat = invokeDetectFormat(logFile, "192.168.1.1 - - [log]");
        String unknownFormat = invokeDetectFormat(unknownFile, "some content");

        assertEquals("json", jsonFormat);
        assertEquals("csv", csvFormat);
        assertEquals("json", logFormat);
        assertEquals("json", unknownFormat); // default
    }

    @Test
    void extractSourceFromFilename_RemovesExtensionAndNormalizes() {
        // given
        String filename1 = "test-service.log";
        String filename2 = "app_server-01.JSON";
        String filename4 = "simple";

        // when & then - используем рефлексию
        String source1 = invokeExtractSource(filename1);
        String source2 = invokeExtractSource(filename2);
        String source4 = invokeExtractSource(filename4);

        assertEquals("test-service", source1);
        assertEquals("app_server-01_json", source2);
        assertEquals("simple", source4);
    }

    @Test
    void moveProcessedFile_WithNoErrors_MovesToProcessedDir() throws IOException {
        // given
        Path sourceFile = tempDir.resolve("success.log");
        Files.writeString(sourceFile, "test content");

        // Создаем объект FileProcessingResult через рефлексию или используем публичный конструктор
        Object result = createFileProcessingResult(10, 0);

        // when
        invokeMoveProcessedFile(sourceFile, result);

        // then
        Path processedDir = tempDir.resolve("processed");
        assertTrue(Files.exists(processedDir));

        // Файл должен быть перемещен в processed
        try (var files = Files.list(processedDir)) {
            assertTrue(files.findAny().isPresent());
        }
        assertFalse(Files.exists(sourceFile));
    }

    @Test
    void moveProcessedFile_WithErrors_MovesToErrorDir() throws IOException {
        // given
        Path sourceFile = tempDir.resolve("error.log");
        Files.writeString(sourceFile, "test content");

        // Создаем объект FileProcessingResult
        Object result = createFileProcessingResult(5, 2);

        // when
        invokeMoveProcessedFile(sourceFile, result);

        // then
        Path errorDir = tempDir.resolve("error");
        assertTrue(Files.exists(errorDir));

        // Файл должен быть перемещен в error
        try (var files = Files.list(errorDir)) {
            assertTrue(files.findAny().isPresent());
        }
        assertFalse(Files.exists(sourceFile));
    }

    @Test
    void moveProcessedFile_WhenTargetExists_CreatesUniqueName() throws IOException {
        // given
        Path sourceFile = tempDir.resolve("duplicate.log");
        Files.writeString(sourceFile, "test content");

        // Создаем целевую директорию и файл с таким же именем
        Path processedDir = tempDir.resolve("processed");
        Files.createDirectories(processedDir);
        Path existingFile = processedDir.resolve("duplicate.log");
        Files.writeString(existingFile, "existing content");

        Object result = createFileProcessingResult(10, 0);

        // when
        invokeMoveProcessedFile(sourceFile, result);

        // then
        // Исходный файл должен быть перемещен с другим именем
        try (var files = Files.list(processedDir)) {
            long count = files.count();
            assertEquals(2, count); // существующий + новый
        }
        assertFalse(Files.exists(sourceFile));
    }

    // Вспомогательные методы для вызова приватных методов через рефлексию
    @SuppressWarnings("unchecked")
    private String invokeDetectFormat(String filename, String firstLine) {
        try {
            var method = FileIngestionService.class.getDeclaredMethod("detectFormat", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(fileIngestionService, filename, firstLine);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeExtractSource(String filename) {
        try {
            var method = FileIngestionService.class.getDeclaredMethod("extractSourceFromFilename", String.class);
            method.setAccessible(true);
            return (String) method.invoke(fileIngestionService, filename);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeMoveProcessedFile(Path file, Object result) {
        try {
            var method = FileIngestionService.class.getDeclaredMethod("moveProcessedFile", Path.class,
                    Class.forName("ru.khkhlv.logcollector.service.ingestion.FileIngestionService$FileProcessingResult"));
            method.setAccessible(true);
            method.invoke(fileIngestionService, file, result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object createFileProcessingResult(int processed, int errors) {
        try {
            Class<?> resultClass = Class.forName("ru.khkhlv.logcollector.service.ingestion.FileIngestionService$FileProcessingResult");
            return resultClass.getDeclaredConstructor(int.class, int.class)
                    .newInstance(processed, errors);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}