package ru.khkhlv.logcollector.service.parser;

import ru.khkhlv.logcollector.model.LogEntry;

public interface LogParser {
    LogEntry parse(String rawLog);
    boolean canParse(String rawLog);
    String getFormatName();
}
