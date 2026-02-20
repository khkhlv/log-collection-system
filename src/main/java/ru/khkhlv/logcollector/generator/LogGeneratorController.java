package ru.khkhlv.logcollector.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST контроллер для управления генератором логов.
 * Позволяет запускать генерацию через HTTP API.
 */
@RestController
@RequestMapping("/api/v1/generator")
@Slf4j
public class LogGeneratorController {

    private final LogGenerator logGenerator;

    public LogGeneratorController(LogGenerator logGenerator) {
        this.logGenerator = logGenerator;
    }

    /**
     * Запускает генерацию логов.
     *
     * POST /api/v1/generator/generate
     * {
     *   "count": 200000,
     *   "format": "json",
     *   "method": "file",
     *   "batchSize": 1000
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateLogs(@RequestBody GenerateRequest request) {
        log.info("Received generation request: {}", request);

        try {
            long startTime = System.currentTimeMillis();
            logGenerator.generateLogs(
                    request.getCount(),
                    request.getFormat(),
                    request.getMethod(),
                    request.getBatchSize()
            );
            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", request.getCount(),
                    "format", request.getFormat(),
                    "method", request.getMethod(),
                    "durationMs", duration,
                    "logsPerSecond", request.getCount() * 1000L / duration
            ));
        } catch (Exception e) {
            log.error("Failed to generate logs", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * DTO для запроса генерации.
     */
    public static class GenerateRequest {
        private int count = 1000;
        private String format = "json";
        private String method = "file";
        private int batchSize = 1000;

        public GenerateRequest() {
        }

        public GenerateRequest(int count, String format, String method, int batchSize) {
            this.count = count;
            this.format = format;
            this.method = method;
            this.batchSize = batchSize;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
