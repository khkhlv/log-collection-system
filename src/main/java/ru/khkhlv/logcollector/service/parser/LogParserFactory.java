package ru.khkhlv.logcollector.service.parser;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LogParserFactory {

    private final Map<String, LogParser> parsers;

    public LogParserFactory(List<LogParser> parserList) {
        this.parsers = parserList.stream()
                .collect(Collectors.toMap(
                        LogParser::getFormatName,
                        Function.identity()
                ));
    }

    public LogParser getParser(String format) {
        LogParser parser = parsers.get(format.toLowerCase());
        if (parser == null) {
            throw new IllegalArgumentException("Unknown format: " + format);
        }
        return parser;
    }

    public LogParser detectParser(String rawLog) {
        return parsers.values().stream()
                .filter(p -> p.canParse(rawLog))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cannot detect format"));
    }
}