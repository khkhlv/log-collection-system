package ru.khkhlv.logcollector.service.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.khkhlv.logcollector.service.LogService;
import ru.khkhlv.logcollector.service.parser.LogParserFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileIngestionService {

    private final LogService logService;
    private final LogParserFactory parserFactory;

    public FileIngestionService(LogService logService, LogParserFactory parserFactory) {
        this.logService = logService;
        this.parserFactory = parserFactory;
    }

    @Value("${app.ingestion.file.watch-dir}")
    private String watchDir;

    @Scheduled(fixedDelayString = "${app.ingestion.file.poll-interval}")
    public void pollForNewFiles() {
        Path directory = Path.of(watchDir);

        if (!Files.exists(directory)) {
            log.warn("Watch directory does not exist: {}", watchDir);
            return;
        }

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> logFiles = files
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".log") || name.endsWith(".json") ||
                                name.endsWith(".txt") || name.endsWith(".csv");
                    })
                    .collect(Collectors.toList());

            for (Path file : logFiles) {
                processFile(file);
                Path processedDir = directory.resolve("processed");
                Files.createDirectories(processedDir);

                // Перемещаем обработанный файл
                Path processed = processedDir.resolve(file.getFileName());
                Files.move(file, processed, StandardCopyOption.ATOMIC_MOVE);
            }

        } catch (IOException e) {
            log.error("Error polling for log files", e);
        }
    }

    private void processFile(Path file) {
        try {
            String content = Files.readString(file);
            String[] lines = content.split("\n");

            String format = detectFormat(file.getFileName().toString(), lines[0]);
            String source = extractSourceFromFilename(file.getFileName().toString());

            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    logService.ingestRawLog(line.trim(), format, source);
                } catch (Exception e) {
                    log.error("Failed to parse line: {}", line, e);
                }
            }
            log.info("Processed file: {} with {} lines", file, lines.length);

        } catch (IOException e) {
            log.error("Failed to read file: {}", file, e);
        }
    }

    private String detectFormat(String filename, String firstLine) {
        if (filename.endsWith(".json")) return "json";
        if (parserFactory.getParser("syslog").canParse(firstLine)) return "syslog";
        if (parserFactory.getParser("clf").canParse(firstLine)) return "clf";
        return "json"; // default
    }

    private String extractSourceFromFilename(String filename) {
        return filename.replaceAll("\\.(log|json|txt|csv)$", "");
    }
}