package com.example.demo.Repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RepositoryResultMapperTest {

    @Test
    void normalizesSqlTemporalValuesAndPreservesOrder() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", Timestamp.valueOf("2026-01-02 03:04:05"));
        row.put("date", Date.valueOf("2026-01-02"));
        row.put("time", Time.valueOf("03:04:05"));
        row.put("text", "value");

        Map<String, Object> normalized = RepositoryResultMapper.normalizeMap(row);

        assertThat(normalized.keySet()).containsExactly("timestamp", "date", "time", "text");
        assertThat(normalized.get("timestamp")).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        assertThat(normalized.get("date")).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(normalized.get("time")).isEqualTo(LocalTime.of(3, 4, 5));
        assertThat(normalized.get("text")).isEqualTo("value");
    }

    @Test
    void normalizesListsAndOptionals() {
        Map<String, Object> row = Map.of("date", Date.valueOf("2026-01-02"));

        assertThat(RepositoryResultMapper.normalizeList(List.of(row))).singleElement()
                .satisfies(result -> assertThat(result.get("date")).isInstanceOf(LocalDate.class));
        assertThat(RepositoryResultMapper.normalizeOptional(Optional.of(row))).isPresent();
        assertThat(RepositoryResultMapper.normalizeOptional(Optional.empty())).isEmpty();
    }
}
