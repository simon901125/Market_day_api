package com.example.demo.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StallRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private StallRepository stallRepository;

    private LocalDateTime paymentDueAt;

    @BeforeEach
    void setUp() {
        paymentDueAt = LocalDateTime.of(2026, 7, 26, 20, 0);
    }

    @Test
    void createEventApplicationReturnsIdFromSqlServerOutputClause() {
        when(namedParameterJdbcTemplate.queryForObject(
                any(String.class),
                any(MapSqlParameterSource.class),
                eq(Long.class)))
                .thenReturn(1L);

        Long applicationId = createEventApplication();

        assertThat(applicationId).isEqualTo(1L);
        verify(namedParameterJdbcTemplate).queryForObject(
                contains("OUTPUT INSERTED.id"),
                any(MapSqlParameterSource.class),
                eq(Long.class));
    }

    @Test
    void createEventApplicationRejectsInvalidGeneratedId() {
        when(namedParameterJdbcTemplate.queryForObject(
                any(String.class),
                any(MapSqlParameterSource.class),
                eq(Long.class)))
                .thenReturn(0L);

        assertThatThrownBy(this::createEventApplication)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event application was created without a valid generated id");
    }

    @Test
    void existsVendorApplicationOnlyCountsNonCancelledApplications() {
        when(namedParameterJdbcTemplate.queryForObject(
                any(String.class),
                anyMap(),
                eq(Integer.class)))
                .thenReturn(1);

        boolean exists = stallRepository.existsVendorApplication(10L, 20L);

        assertThat(exists).isTrue();
        verify(namedParameterJdbcTemplate).queryForObject(
                contains("AND is_cancelled = 0"),
                anyMap(),
                eq(Integer.class));
    }

    @Test
    void existsVendorApplicationAllowsReapplicationWhenNoActiveApplicationExists() {
        when(namedParameterJdbcTemplate.queryForObject(
                any(String.class),
                anyMap(),
                eq(Integer.class)))
                .thenReturn(0);

        assertThat(stallRepository.existsVendorApplication(10L, 20L)).isFalse();
    }

    private Long createEventApplication() {
        return stallRepository.createEventApplication(
                "MD202607250001",
                1L,
                4L,
                2L,
                null,
                null,
                new BigDecimal("1800"),
                new BigDecimal("500"),
                paymentDueAt);
    }
}
