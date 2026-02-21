package ru.khkhlv.logcollector.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.khkhlv.logcollector.service.etl.EtlService;
import ru.khkhlv.logcollector.service.metrics.LogMetricsCollector;
import ru.khkhlv.logcollector.service.parser.LogParserFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileIngestionService {

    private final EtlService etlValidationService;
    private final LogParserFactory parserFactory;
    private final LogMetricsCollector metricsCollector;

    @Value("${app.ingestion.file.watch-dir}")
    private String watchDir;

    @Value("${app.ingestion.file.processed-dir:processed}")
    private String processedDirName;

    @Value("${app.ingestion.file.error-dir:error}")
    private String errorDirName;

    @Value("${app.ingestion.file.batch-size:1000}")
    private int batchSize;

    public FileIngestionService(
            EtlService etlValidationService,
            LogParserFactory parserFactory,
            LogMetricsCollector metricsCollector) {
        this.etlValidationService = etlValidationService;
        this.parserFactory = parserFactory;
        this.metricsCollector = metricsCollector;
    }

    @Scheduled(fixedDelayString = "${app.ingestion.file.poll-interval}")
    public void pollForNewFiles() {
        Path directory = Path.of(watchDir);

        if (!Files.exists(directory)) {
            log.warn("Watch directory does not exist: {}", watchDir);
            return;
        }

        log.info("Polling for new files in: {}", watchDir);
        long startTime = System.currentTimeMillis();

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> logFiles = files
                    .filter(Files::isRegularFile)
                    .filter(this::isLogFile)
                    .collect(Collectors.toList());

            if (logFiles.isEmpty()) {
                log.debug("No new files found");
                return;
            }

            log.info("Found {} files to process", logFiles.size());

            AtomicInteger totalProcessed = new AtomicInteger(0);
            AtomicInteger totalErrors = new AtomicInteger(0);

            for (Path file : logFiles) {
                FileProcessingResult result = processFile(file);
                totalProcessed.addAndGet(result.processedCount);
                totalErrors.addAndGet(result.errorCount);

                // Перемещаем файл после обработки
                moveProcessedFile(file, result);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("File ingestion completed: processed={}, errors={}, duration={}ms",
                    totalProcessed.get(), totalErrors.get(), duration);

        } catch (IOException e) {
            log.error("Error polling for log files", e);
        }
    }

    private boolean isLogFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".log") || name.endsWith(".json") ||
                name.endsWith(".txt") || name.endsWith(".csv") ||
                name.endsWith(".syslog");
    }

    private FileProcessingResult processFile(Path file) {
        log.info("Processing file: {}", file.getFileName());

        int processed = 0;
        int errors = 0;

        try {
            String content = Files.readString(file);
            String[] lines = content.split("\n");

            // Определяем формат файла
            String firstLine = lines.length > 0 ? lines[0] : "";
            String format = detectFormat(file.getFileName().toString(), firstLine);
            String source = extractSourceFromFilename(file.getFileName().toString());

            log.debug("File format: {}, source: {}, lines: {}", format, source, lines.length);

            // Обрабатываем строки батчами
            List<String> batch = new ArrayList<>();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                batch.add(line);

                // Когда набрали батч или это последняя строка
                if (batch.size() >= batchSize || i == lines.length - 1) {
                    try {
                        var results = etlValidationService.processLogs(batch, format, source);
                        processed += results.size();
                        // Ошибки логируются внутри ETL
                    } catch (Exception e) {
                        errors += batch.size();
                        log.error("Failed to process batch of {} lines from file {}",
                                batch.size(), file.getFileName(), e);
                    }
                    batch.clear();

                    // Небольшая пауза между батчами
                    if (i < lines.length - 1) {
                        Thread.sleep(5);
                    }
                }
            }

            log.info("File processed: {} (success={}, errors={})",
                    file.getFileName(), processed, errors);

        } catch (IOException e) {
            log.error("Failed to read file: {}", file, e);
            errors++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("File processing interrupted", e);
        }

        return new FileProcessingResult(processed, errors);
    }

    private void moveProcessedFile(Path file, FileProcessingResult result) {
        try {
            Path baseDir = file.getParent();
            if (baseDir == null) {
                baseDir = Path.of(".");
            }

            Path targetDir;

            // Определяем целевую директорию в зависимости от результата
            if (result.errorCount == 0) {
                targetDir = baseDir.resolve(processedDirName);
            } else {
                targetDir = baseDir.resolve(errorDirName);
            }

            // Создаем директории, если их нет
            Files.createDirectories(targetDir);

            // Добавляем timestamp к имени файла только если были ошибки
            String newFilename;
            if (result.errorCount > 0) {
                String timestamp = Instant.now().toString()
                        .replace(":", "-")
                        .replace(".", "-");
                newFilename = "ERROR_" + timestamp + "_" + file.getFileName().toString();
            } else {
                newFilename = file.getFileName().toString();
            }

            Path target = targetDir.resolve(newFilename);

            // Если файл уже существует, добавляем уникальный суффикс
            if (Files.exists(target)) {
                String baseName = newFilename;
                String extension = "";
                int dotIndex = baseName.lastIndexOf('.');
                if (dotIndex > 0) {
                    extension = baseName.substring(dotIndex);
                    baseName = baseName.substring(0, dotIndex);
                }
                target = targetDir.resolve(baseName + "_" + System.currentTimeMillis() + extension);
            }

            Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);

            log.debug("Moved file to: {}", target);

        } catch (IOException e) {
            log.error("Failed to move processed file: {}", file, e);
        }
    }

    private String detectFormat(String filename, String firstLine) {
        if (filename.endsWith(".json")) return "json";
        if (filename.endsWith(".csv")) return "csv";

        try {
            if (parserFactory.getParser("syslog").canParse(firstLine)) return "syslog";
            if (parserFactory.getParser("clf").canParse(firstLine)) return "clf";
        } catch (Exception e) {
            log.debug("Could not detect format from content, using default");
        }

        return "json"; // default
    }

    private String extractSourceFromFilename(String filename) {
        // Убираем расширение
        String base = filename.replaceAll("\\.(log|json|txt|csv|syslog)$", "");
        // Заменяем недопустимые символы
        return base.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    /**
     * Ручная обработка конкретного файла
     */
    public void processSpecificFile(String filepath) {
        Path file = Path.of(filepath);
        if (Files.exists(file) && isLogFile(file)) {
            log.info("Manually processing file: {}", file);
            processFile(file);
        } else {
            log.warn("File not found or not a log file: {}", filepath);
        }
    }

    /**
     * Результат обработки файла
     */
    public static class FileProcessingResult {
        final int processedCount;
        final int errorCount;

        FileProcessingResult(int processedCount, int errorCount) {
            this.processedCount = processedCount;
            this.errorCount = errorCount;
        }
    }
}

