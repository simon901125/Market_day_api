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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.MarketEventCardResponse;
import com.example.demo.dto.response.MarketEventDetailResponse;
import com.example.demo.dto.response.CategoryResponse;

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
                    CAST(e.start_at AS DATE) AS start_date,
                    CAST(e.end_at AS DATE) AS end_date,
                    e.cover_image_url,
                    e.workflow_status AS publish_status
                FROM dbo.market_events e
                WHERE e.workflow_status = N'PUBLISHED'
                """);

        Map<String, Object> params = new HashMap<>();
        appendKeywordFilter(sql, params, request);
        appendCitiesFilter(sql, params, request);
        appendCategoryIdsFilter(sql, params, request);
        appendDateRangeFilter(sql, params, request);
        appendEventTypeFilter(sql, request);
        appendEventStatusesFilter(sql, params, request);

        sql.append(" ORDER BY e.start_at DESC, e.id DESC");
        List<MarketEventCardResponse> cards = namedParameterJdbcTemplate.query(
                sql.toString(), params, this::toMarketEventCardResponse);
        Map<Long, List<CategoryResponse>> categoriesByEventId = findCategoriesByEventIds(
                cards.stream().map(MarketEventCardResponse::id).toList());
        return cards.stream()
                .map(card -> withCategories(card, categoriesByEventId.getOrDefault(card.id(), List.of())))
                .toList();
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
                    CONCAT_WS(N' / ', e.traffic_info_metro, e.traffic_info_bus, e.traffic_info_driving) AS traffic_info,
                    e.notice,
                    CAST(e.start_at AS DATE) AS start_date,
                    CAST(e.end_at AS DATE) AS end_date,
                    CAST(e.start_at AS TIME) AS start_time,
                    CAST(e.end_at AS TIME) AS end_time,
                    e.registration_start_at,
                    e.registration_end_at,
                    e.max_booths,
                    e.base_fee,
                    e.cover_image_url,
                    e.map_image_url,
                    e.public_info_at,
                    e.workflow_status AS review_status,
                    e.workflow_status AS publish_status
                FROM dbo.market_events e
                WHERE e.id = :id
                  AND e.workflow_status = N'PUBLISHED'
                """;

        Map<String, Object> params = Map.of("id", id);
        return namedParameterJdbcTemplate.query(sql, params, this::toMarketEventDetailResponse)
                .stream()
                .findFirst()
                .map(detail -> withCategories(
                        detail,
                        findCategoriesByEventIds(List.of(detail.id())).getOrDefault(detail.id(), List.of())));
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

    private void appendCategoryIdsFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        List<Long> categoryIds = request == null || request.categoryIds() == null
                ? List.of()
                : request.categoryIds().stream().filter(id -> id != null && id > 0).distinct().toList();
        if (categoryIds.isEmpty()) {
            return;
        }
        sql.append("""
                 AND EXISTS (
                     SELECT 1
                     FROM dbo.market_event_categories mec
                     WHERE mec.event_id = e.id
                       AND mec.category_id IN (:categoryIds)
                 )
                """);
        params.put("categoryIds", categoryIds);
    }

    private void appendDateRangeFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        if (request == null) {
            return;
        }

        if (request.startDate() != null) {
            sql.append(" AND CAST(e.end_at AS DATE) >= :startDate");
            params.put("startDate", request.startDate());
        }

        if (request.endDate() != null) {
            sql.append(" AND CAST(e.start_at AS DATE) <= :endDate");
            params.put("endDate", request.endDate());
        }
    }

    private void appendEventTypeFilter(StringBuilder sql, MarketSearchRequest request) {
        String eventType = normalizeText(request == null ? null : request.eventType());
        if (eventType == null || "ALL".equalsIgnoreCase(eventType)) {
            return;
        }

        if ("CURRENT".equalsIgnoreCase(eventType)) {
            sql.append(" AND CAST(e.end_at AS DATE) >= CAST(GETDATE() AS DATE)");
            return;
        }

        if ("HISTORY".equalsIgnoreCase(eventType)) {
            sql.append(" AND CAST(e.end_at AS DATE) < CAST(GETDATE() AS DATE)");
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
                    CAST(e.start_at AS DATE) > DATEADD(day, :startingSoonDays, CAST(GETDATE() AS DATE))
                    """);
            hasCondition = true;
        }

        if (statuses.contains("STARTING_SOON")) {
            if (hasCondition) {
                sql.append(" OR ");
            }
            sql.append("""
                    (
                        CAST(e.start_at AS DATE) > CAST(GETDATE() AS DATE)
                        AND CAST(e.start_at AS DATE) <= DATEADD(day, :startingSoonDays, CAST(GETDATE() AS DATE))
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
                        CAST(e.start_at AS DATE) <= CAST(GETDATE() AS DATE)
                        AND CAST(e.end_at AS DATE) >= CAST(GETDATE() AS DATE)
                    )
                    """);
            hasCondition = true;
        }

        if (statuses.contains("ENDED")) {
            if (hasCondition) {
                sql.append(" OR ");
            }
            sql.append("CAST(e.end_at AS DATE) < CAST(GETDATE() AS DATE)");
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
                List.of(),
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
                List.of(),
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

    private Map<Long, List<CategoryResponse>> findCategoriesByEventIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT mec.event_id, c.id, c.name, c.slug
                FROM dbo.market_event_categories mec
                INNER JOIN dbo.categories c ON c.id = mec.category_id
                WHERE mec.event_id IN (:eventIds)
                ORDER BY mec.event_id, c.id
                """;
        Map<Long, List<CategoryResponse>> result = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query(sql, Map.of("eventIds", eventIds), rs -> {
            result.computeIfAbsent(rs.getLong("event_id"), ignored -> new java.util.ArrayList<>())
                    .add(new CategoryResponse(rs.getLong("id"), rs.getString("name"), rs.getString("slug")));
        });
        return result;
    }

    private static MarketEventCardResponse withCategories(
            MarketEventCardResponse card, List<CategoryResponse> categories) {
        return new MarketEventCardResponse(card.id(), card.title(), card.summary(), card.locationName(),
                card.city(), card.district(), card.address(), card.startDate(), card.endDate(),
                card.coverImageUrl(), card.publishStatus(), categories, card.eventStatus());
    }

    private static MarketEventDetailResponse withCategories(
            MarketEventDetailResponse detail, List<CategoryResponse> categories) {
        return new MarketEventDetailResponse(detail.id(), detail.title(), detail.summary(), detail.description(),
                categories, detail.locationName(), detail.city(), detail.district(), detail.address(),
                detail.trafficInfo(), detail.notice(), detail.startDate(), detail.endDate(), detail.startTime(),
                detail.endTime(), detail.registrationStartAt(), detail.registrationEndAt(), detail.maxBooths(),
                detail.baseFee(), detail.coverImageUrl(), detail.mapImageUrl(), detail.publicInfoAt(),
                detail.reviewStatus(), detail.publishStatus(), detail.eventStatus());
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
