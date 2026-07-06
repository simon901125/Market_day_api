package com.example.demo.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.MarketEventCardResponse;
import com.example.demo.dto.response.MarketEventDetailResponse;

@Repository
public class MarketEventRepository {

    private static final int STARTING_SOON_DAYS = 7;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<MarketEventCardResponse> searchMarketEvents(MarketSearchRequest request) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    e.id,
                    e.title,
                    e.summary,
                    e.location_name,
                    e.city,
                    e.district,
                    e.address,
                    e.start_date,
                    e.end_date,
                    COALESCE(e.cover_image_url, first_image.image_url) AS cover_image_url,
                    e.publish_status,
                    c.name AS category_name
                FROM dbo.market_events e
                INNER JOIN dbo.categories c ON c.id = e.category_id
                OUTER APPLY (
                    SELECT TOP 1 image_url
                    FROM dbo.event_images
                    WHERE event_id = e.id
                    ORDER BY id
                ) first_image
                WHERE e.publish_status = N'PUBLISHED'
                  AND e.review_status = N'APPROVED'
                """);

        Map<String, Object> params = new HashMap<>();
        appendKeywordFilter(sql, params, request);
        appendCitiesFilter(sql, params, request);
        appendCategoryNamesFilter(sql, params, request);
        appendDateRangeFilter(sql, params, request);
        appendEventTypeFilter(sql, request);
        appendEventStatusesFilter(sql, params, request);

        sql.append(" ORDER BY e.start_date DESC, e.id DESC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, this::toMarketEventCardResponse);
    }

    public Optional<MarketEventDetailResponse> findMarketEventDetailById(Long id) {
        String sql = """
                SELECT
                    e.id,
                    e.title,
                    e.summary,
                    e.description,
                    e.location_name,
                    e.city,
                    e.district,
                    e.address,
                    e.traffic_info,
                    e.notice,
                    e.start_date,
                    e.end_date,
                    e.start_time,
                    e.end_time,
                    e.registration_start_at,
                    e.registration_end_at,
                    e.max_booths,
                    e.base_fee,
                    COALESCE(e.cover_image_url, first_image.image_url) AS cover_image_url,
                    e.map_image_url,
                    e.public_info_at,
                    e.review_status,
                    e.publish_status,
                    c.name AS category_name
                FROM dbo.market_events e
                INNER JOIN dbo.categories c ON c.id = e.category_id
                OUTER APPLY (
                    SELECT TOP 1 image_url
                    FROM dbo.event_images
                    WHERE event_id = e.id
                    ORDER BY id
                ) first_image
                WHERE e.id = :id
                  AND e.publish_status = N'PUBLISHED'
                  AND e.review_status = N'APPROVED'
                """;

        Map<String, Object> params = Map.of("id", id);
        return namedParameterJdbcTemplate.query(sql, params, this::toMarketEventDetailResponse)
                .stream()
                .findFirst();
    }

    private void appendKeywordFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        String keyword = normalizeText(request == null ? null : request.keyword());
        if (keyword == null) {
            return;
        }

        sql.append("""
                  AND (
                      e.title LIKE :keyword
                      OR e.summary LIKE :keyword
                      OR e.location_name LIKE :keyword
                      OR e.city LIKE :keyword
                      OR e.district LIKE :keyword
                      OR e.address LIKE :keyword
                  )
                """);
        params.put("keyword", "%" + keyword + "%");
    }

    private void appendCitiesFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        List<String> cities = normalizeList(request == null ? null : request.cities());
        if (cities.isEmpty()) {
            return;
        }

        sql.append(" AND e.city IN (:cities)");
        params.put("cities", cities);
    }

    private void appendCategoryNamesFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        List<String> categoryNames = normalizeList(request == null ? null : request.categoryNames());
        if (categoryNames.isEmpty()) {
            return;
        }

        sql.append(" AND c.name IN (:categoryNames)");
        params.put("categoryNames", categoryNames);
    }

    private void appendDateRangeFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        if (request == null) {
            return;
        }

        if (request.startDate() != null) {
            sql.append(" AND e.end_date >= :startDate");
            params.put("startDate", request.startDate());
        }

        if (request.endDate() != null) {
            sql.append(" AND e.start_date <= :endDate");
            params.put("endDate", request.endDate());
        }
    }

    private void appendEventTypeFilter(StringBuilder sql, MarketSearchRequest request) {
        String eventType = normalizeText(request == null ? null : request.eventType());
        if (eventType == null || "ALL".equalsIgnoreCase(eventType)) {
            return;
        }

        if ("CURRENT".equalsIgnoreCase(eventType)) {
            sql.append(" AND e.end_date >= CAST(GETDATE() AS DATE)");
            return;
        }

        if ("HISTORY".equalsIgnoreCase(eventType)) {
            sql.append(" AND e.end_date < CAST(GETDATE() AS DATE)");
        }
    }

    private void appendEventStatusesFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        List<String> statuses = normalizeList(request == null ? null : request.eventStatuses()).stream()
                .map(String::toUpperCase)
                .distinct()
                .toList();
        if (statuses.isEmpty()) {
            return;
        }

        if (statuses.contains("UPCOMING") || statuses.contains("STARTING_SOON")) {
            params.put("startingSoonDays", STARTING_SOON_DAYS);
        }

        sql.append(" AND (");
        boolean hasCondition = false;

        if (statuses.contains("UPCOMING")) {
            sql.append("""
                    e.start_date > DATEADD(day, :startingSoonDays, CAST(GETDATE() AS DATE))
                    """);
            hasCondition = true;
        }

        if (statuses.contains("STARTING_SOON")) {
            if (hasCondition) {
                sql.append(" OR ");
            }
            sql.append("""
                    (
                        e.start_date > CAST(GETDATE() AS DATE)
                        AND e.start_date <= DATEADD(day, :startingSoonDays, CAST(GETDATE() AS DATE))
                    )
                    """);
            hasCondition = true;
        }

        if (statuses.contains("ONGOING")) {
            if (hasCondition) {
                sql.append(" OR ");
            }
            sql.append("""
                    (
                        e.start_date <= CAST(GETDATE() AS DATE)
                        AND e.end_date >= CAST(GETDATE() AS DATE)
                    )
                    """);
            hasCondition = true;
        }

        if (statuses.contains("ENDED")) {
            if (hasCondition) {
                sql.append(" OR ");
            }
            sql.append("e.end_date < CAST(GETDATE() AS DATE)");
        }

        sql.append(")");
    }

    private MarketEventCardResponse toMarketEventCardResponse(ResultSet rs, int rowNum) throws SQLException {
        LocalDate startDate = toLocalDate(rs.getDate("start_date"));
        LocalDate endDate = toLocalDate(rs.getDate("end_date"));

        return new MarketEventCardResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("location_name"),
                rs.getString("city"),
                rs.getString("district"),
                rs.getString("address"),
                startDate,
                endDate,
                rs.getString("cover_image_url"),
                rs.getString("publish_status"),
                toCategoryNames(rs.getString("category_name")),
                resolveEventStatus(startDate, endDate));
    }

    private MarketEventDetailResponse toMarketEventDetailResponse(ResultSet rs, int rowNum) throws SQLException {
        LocalDate startDate = toLocalDate(rs.getDate("start_date"));
        LocalDate endDate = toLocalDate(rs.getDate("end_date"));

        return new MarketEventDetailResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("description"),
                toCategoryNames(rs.getString("category_name")),
                rs.getString("location_name"),
                rs.getString("city"),
                rs.getString("district"),
                rs.getString("address"),
                rs.getString("traffic_info"),
                rs.getString("notice"),
                startDate,
                endDate,
                toLocalTime(rs.getTime("start_time")),
                toLocalTime(rs.getTime("end_time")),
                toLocalDateTime(rs.getTimestamp("registration_start_at")),
                toLocalDateTime(rs.getTimestamp("registration_end_at")),
                rs.getObject("max_booths", Integer.class),
                rs.getBigDecimal("base_fee"),
                rs.getString("cover_image_url"),
                rs.getString("map_image_url"),
                toLocalDateTime(rs.getTimestamp("public_info_at")),
                rs.getString("review_status"),
                rs.getString("publish_status"),
                resolveEventStatus(startDate, endDate));
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static LocalTime toLocalTime(Time time) {
        return time == null ? null : time.toLocalTime();
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static List<String> toCategoryNames(String categoryName) {
        String normalizedCategoryName = normalizeText(categoryName);
        return normalizedCategoryName == null ? List.of() : List.of(normalizedCategoryName);
    }

    private static String resolveEventStatus(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();

        if (endDate != null && today.isAfter(endDate)) {
            return "ENDED";
        }

        if (startDate != null && today.isBefore(startDate)) {
            return today.plusDays(STARTING_SOON_DAYS).isBefore(startDate)
                    ? "UPCOMING"
                    : "STARTING_SOON";
        }

        return "ONGOING";
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(MarketEventRepository::normalizeText)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }
}
