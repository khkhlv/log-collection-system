package ru.khkhlv.logcollector.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionResponse {
    private boolean success;
    private int processedCount;
    private Instant timestamp;
}

