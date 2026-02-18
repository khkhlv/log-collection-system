package ru.khkhlv.logcollector.controller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.khkhlv.logcollector.model.LogEntry;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogSearchResponse {
    private long total;
    private int page;
    private int size;
    private List<LogEntry> logs;
}

