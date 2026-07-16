package com.example.demo.Repository;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.LinkedCaseInsensitiveMap;

public final class RepositoryResultMapper {

    private RepositoryResultMapper() {
    }

    public static List<Map<String, Object>> normalizeList(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(RepositoryResultMapper::normalizeMap)
                .toList();
    }

    public static Optional<Map<String, Object>> normalizeOptional(Optional<Map<String, Object>> row) {
        return row.map(RepositoryResultMapper::normalizeMap);
    }

    public static Map<String, Object> normalizeMap(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedCaseInsensitiveMap<>();
        row.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return normalized;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        return value;
    }
}
