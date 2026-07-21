package com.example.demo.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import com.example.demo.dto.response.MarketEventDetailResponse.OrganizerInfo;
import com.example.demo.dto.response.MarketEventDetailResponse.SelectedStall;
import com.example.demo.dto.response.MarketEventDetailResponse.StallBrand;
import com.example.demo.dto.response.MarketEventDetailResponse.TrafficInfo;
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
                    e.cover_image_url
                FROM dbo.market_events e
                WHERE e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                """);

        Map<String, Object> params = new HashMap<>();
        appendKeywordFilter(sql, params, request);
        appendCityFilter(sql, params, request);
        appendCategoryNamesFilter(sql, params, request);
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
                    e.traffic_info_metro,
                    e.traffic_info_bus,
                    e.traffic_info_driving,
                    CAST(e.start_at AS DATE) AS start_date,
                    CAST(e.end_at AS DATE) AS end_date,
                    CAST(e.start_at AS TIME) AS start_time,
                    CAST(e.end_at AS TIME) AS end_time,
                    e.cover_image_url,
                    CASE
                        WHEN e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                         AND e.brands_public_at IS NOT NULL
                         AND e.brands_public_at <= SYSDATETIME()
                        THEN e.map_image_url
                        ELSE NULL
                    END AS map_image_url,
                    CAST(CASE
                        WHEN e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                         AND e.brands_public_at IS NOT NULL
                         AND e.brands_public_at <= SYSDATETIME()
                        THEN 1
                        ELSE 0
                    END AS BIT) AS brands_public,
                    op.organizer_name,
                    up.contact_email,
                    up.contact_phone,
                    op.service_days,
                    op.service_start_time,
                    op.service_end_time
                FROM dbo.market_events e
                LEFT JOIN dbo.user_profiles up
                    ON up.user_id = e.user_id AND up.profile_type = N'ORGANIZER'
                LEFT JOIN dbo.organizer_profiles op ON op.user_profile_id = up.id
                WHERE e.id = :id
                  AND e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                """;

        Map<String, Object> params = Map.of("id", id);
        return namedParameterJdbcTemplate.query(sql, params, this::toMarketEventDetailResponse)
                .stream()
                .findFirst()
                .map(detail -> withCategories(
                        detail,
                        findCategoriesByEventIds(List.of(detail.id())).getOrDefault(detail.id(), List.of())));
    }

    public Optional<SelectedStall> findPublicSelectedStall(Long eventId, LocalDate date, String stallNo) {
        String sql = """
                SELECT s.stall_no,
                       vp.id AS vendor_profile_id, vp.brand_name, vp.brand_summary,
                       vp.facebook_url, vp.instagram_url, vp.website_url,
                       vp.avatar_image_url, vp.cover_image_url,
                       c.id AS category_id, c.name AS category_name, c.slug AS category_slug
                FROM dbo.event_stalls s
                INNER JOIN dbo.market_events e ON e.id = s.event_id
                LEFT JOIN dbo.application_dates ad
                    ON ad.selected_stall_id = s.id AND ad.apply_date = :date
                LEFT JOIN dbo.event_applications ea
                    ON ea.id = ad.application_id
                   AND ea.event_id = s.event_id
                   AND ea.is_cancelled = 0
                   AND ea.review_status = N'APPROVED'
                LEFT JOIN dbo.vendor_profiles vp ON vp.id = ea.vendor_profile_id
                LEFT JOIN dbo.categories c ON c.id = vp.category_id
                WHERE s.event_id = :eventId
                  AND s.stall_no = :stallNo
                  AND e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                  AND e.brands_public_at IS NOT NULL
                  AND e.brands_public_at <= SYSDATETIME()
                """;
        Map<String, Object> params = Map.of("eventId", eventId, "date", date, "stallNo", stallNo);
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Long vendorProfileId = rs.getObject("vendor_profile_id", Long.class);
            StallBrand brand = vendorProfileId == null ? null : new StallBrand(
                    vendorProfileId,
                    rs.getString("brand_name"),
                    new CategoryResponse(rs.getLong("category_id"), rs.getString("category_name"),
                            rs.getString("category_slug")),
                    rs.getString("brand_summary"),
                    rs.getString("facebook_url"),
                    rs.getString("instagram_url"),
                    rs.getString("website_url"),
                    rs.getString("avatar_image_url"),
                    rs.getString("cover_image_url"));
            return new SelectedStall(
                    rs.getString("stall_no"), brand);
        }).stream().findFirst();
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

    private void appendCityFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        String city = normalizeText(request == null ? null : request.city());
        if (city == null) {
            return;
        }

        sql.append(" AND e.city = :city");
        params.put("city", city);
    }

    private void appendCategoryNamesFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        List<String> categoryNames = normalizeList(request == null ? null : request.categoryNames());
        if (categoryNames.isEmpty()) {
            return;
        }
        sql.append("""
                 AND EXISTS (
                     SELECT 1
                     FROM dbo.market_event_categories mec
                     INNER JOIN dbo.categories c ON c.id = mec.category_id
                     WHERE mec.event_id = e.id
                       AND c.is_active = 1
                       AND c.name IN (:categoryNames)
                 )
                """);
        params.put("categoryNames", categoryNames);
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
        if (eventType == null) {
            return;
        }

        if ("目前活動".equals(eventType)) {
            sql.append(" AND CAST(e.end_at AS DATE) >= CAST(GETDATE() AS DATE)");
            return;
        }

        if ("歷史活動".equals(eventType)) {
            sql.append(" AND CAST(e.end_at AS DATE) < CAST(GETDATE() AS DATE)");
        }
    }

    private void appendEventStatusesFilter(StringBuilder sql, Map<String, Object> params, MarketSearchRequest request) {
        List<String> statuses = normalizeList(request == null ? null : request.eventStatuses()).stream()
                .distinct()
                .toList();
        if (statuses.isEmpty()) {
            return;
        }

        if (statuses.contains("活動預告") || statuses.contains("即將開始")) {
            params.put("startingSoonDays", STARTING_SOON_DAYS);
        }

        sql.append(" AND (");
        boolean hasCondition = false;

        if (statuses.contains("活動預告")) {
            sql.append("""
                    CAST(e.start_at AS DATE) > DATEADD(day, :startingSoonDays, CAST(GETDATE() AS DATE))
                    """);
            hasCondition = true;
        }

        if (statuses.contains("即將開始")) {
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

        if (statuses.contains("進行中")) {
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

        if (statuses.contains("已結束")) {
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
                toChineseDayOfWeek(startDate),
                endDate,
                toChineseDayOfWeek(endDate),
                rs.getString("cover_image_url"),
                List.of(),
                resolveEventStatus(startDate, endDate));
    }

    private MarketEventDetailResponse toMarketEventDetailResponse(ResultSet rs, int rowNum) throws SQLException {
        LocalDate startDate = toLocalDate(rs.getDate("start_date"));
        LocalDate endDate = toLocalDate(rs.getDate("end_date"));

        return new MarketEventDetailResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("cover_image_url"),
                resolveEventStatus(startDate, endDate),
                rs.getString("summary"),
                startDate,
                toChineseDayOfWeek(startDate),
                endDate,
                toChineseDayOfWeek(endDate),
                toLocalTime(rs.getTime("start_time")),
                toLocalTime(rs.getTime("end_time")),
                durationDays(startDate, endDate),
                rs.getString("location_name"),
                rs.getString("city"),
                rs.getString("district"),
                rs.getString("address"),
                rs.getString("description"),
                List.of(),
                new OrganizerInfo(
                        rs.getString("organizer_name"),
                        rs.getString("contact_email"),
                        rs.getString("contact_phone"),
                        rs.getString("service_days"),
                        toLocalTime(rs.getTime("service_start_time")),
                        toLocalTime(rs.getTime("service_end_time"))),
                trafficInfos(rs),
                rs.getBoolean("brands_public"),
                rs.getString("map_image_url"),
                null,
                null);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static LocalTime toLocalTime(Time time) {
        return time == null ? null : time.toLocalTime();
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
                card.city(), card.district(), card.address(), card.startDate(), card.startDayOfWeek(),
                card.endDate(), card.endDayOfWeek(), card.coverImageUrl(), categories, card.eventStatus());
    }

    private static MarketEventDetailResponse withCategories(
            MarketEventDetailResponse detail, List<CategoryResponse> categories) {
        return new MarketEventDetailResponse(detail.id(), detail.title(), detail.coverImageUrl(), detail.eventStatus(),
                detail.summary(), detail.startDate(), detail.startDayOfWeek(), detail.endDate(), detail.endDayOfWeek(),
                detail.startTime(), detail.endTime(), detail.durationDays(), detail.locationName(), detail.city(),
                detail.district(), detail.address(), detail.description(), categories, detail.organizer(),
                detail.trafficInfos(), detail.brandsPublic(), detail.mapImageUrl(),
                detail.selectedDate(), detail.selectedStall());
    }

    private static long durationDays(LocalDate startDate, LocalDate endDate) {
        return startDate == null || endDate == null ? 0 : ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private static List<TrafficInfo> trafficInfos(ResultSet rs) throws SQLException {
        List<TrafficInfo> result = new ArrayList<>();
        addTraffic(result, "捷運", rs.getString("traffic_info_metro"));
        addTraffic(result, "公車", rs.getString("traffic_info_bus"));
        addTraffic(result, "開車", rs.getString("traffic_info_driving"));
        return List.copyOf(result);
    }

    private static void addTraffic(List<TrafficInfo> result, String method, String details) {
        if (details != null && !details.isBlank()) {
            result.add(new TrafficInfo(method, details));
        }
    }

    private static String resolveEventStatus(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();

        if (endDate != null && today.isAfter(endDate)) {
            return "已結束";
        }

        if (startDate != null && today.isBefore(startDate)) {
            return today.plusDays(STARTING_SOON_DAYS).isBefore(startDate)
                    ? "活動預告"
                    : "即將開始";
        }

        return "進行中";
    }

    private static String toChineseDayOfWeek(LocalDate date) {
        if (date == null) {
            return null;
        }
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "(一)";
            case TUESDAY -> "(二)";
            case WEDNESDAY -> "(三)";
            case THURSDAY -> "(四)";
            case FRIDAY -> "(五)";
            case SATURDAY -> "(六)";
            case SUNDAY -> "(日)";
        };
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
