package com.example.demo.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StallRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public Optional<Long> findStallId(Long eventId, String stallNo) {
        String sql = """
                SELECT id
                FROM dbo.event_stalls
                WHERE event_id = :eventId
                  AND stall_no = :stallNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("stallNo", stallNo);

        List<Long> stallIds = namedParameterJdbcTemplate.queryForList(sql, map, Long.class);
        return stallIds.stream().findFirst();
    }

    public Optional<Map<String, Object>> findStallForSelection(Long eventId, String stallNo) {
        String sql = """
                SELECT
                    id,
                    event_id AS eventId,
                    stall_no AS stallNo,
                    status
                FROM dbo.event_stalls
                WHERE event_id = :eventId
                  AND stall_no = :stallNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("stallNo", stallNo);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findApplicationForSelection(String applicationNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.event_id AS eventId,
                    a.user_id AS userId,
                    a.vendor_profile_id AS vendorProfileId,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.is_cancelled AS isCancelled,
                    date_counts.applicationDateCount,
                    date_counts.selectedStallCount
                FROM dbo.event_applications a
                OUTER APPLY (
                    SELECT
                        COUNT(*) AS applicationDateCount,
                        SUM(CASE WHEN ad.selected_stall_id IS NULL THEN 0 ELSE 1 END) AS selectedStallCount
                    FROM dbo.application_dates ad
                    WHERE ad.application_id = a.id
                ) date_counts
                WHERE a.application_no = :applicationNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findSelectableApplication(String applicationNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.event_id AS eventId,
                    a.user_id AS userId,
                    a.vendor_profile_id AS vendorProfileId,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    date_counts.applicationDateCount,
                    date_counts.selectedStallCount
                FROM dbo.event_applications a
                OUTER APPLY (
                    SELECT
                        COUNT(*) AS applicationDateCount,
                        SUM(CASE WHEN ad.selected_stall_id IS NULL THEN 0 ELSE 1 END) AS selectedStallCount
                    FROM dbo.application_dates ad
                    WHERE ad.application_id = a.id
                ) date_counts
                WHERE a.application_no = :applicationNo
                  AND a.review_status = N'APPROVED'
                  AND a.payment_status = N'PAID'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.application_dates ad
                      WHERE ad.application_id = a.id
                        AND ad.selected_stall_id IS NOT NULL
                  )
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findApplicationDatesForSelection(Long applicationId) {
        String sql = """
                SELECT
                    ad.id AS applicationDateId,
                    ad.application_id AS applicationId,
                    ad.apply_date AS applyDate,
                    ad.selected_stall_id AS selectedStallId
                FROM dbo.application_dates ad
                WHERE ad.application_id = :applicationId
                ORDER BY ad.apply_date ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findVendorAccountByEmail(String email) {
        String sql = """
                SELECT
                    u.id AS userId,
                    u.role,
                    u.email,
                    u.provider,
                    up.id AS userProfileId,
                    vp.id AS vendorProfileId,
                    vp.brand_name AS name,
                    up.contact_name AS contactName,
                    up.contact_phone AS contactPhone,
                    up.contact_email AS contactEmail,
                    up.city,
                    up.district,
                    up.address,
                    vp.category_id AS categoryId,
                    c.name AS categoryName,
                    c.slug AS categorySlug,
                    vp.instagram_url AS instagramUrl,
                    vp.facebook_url AS facebookUrl,
                    vp.website_url AS websiteUrl,
                    vp.avatar_image_url AS avatarImageUrl,
                    vp.cover_image_url AS coverImageUrl,
                    vp.brand_description AS brandDescription,
                    c.name AS brandType,
                    vp.brand_summary AS brandSummary
                FROM dbo.users u
                INNER JOIN dbo.user_profiles up ON up.user_id = u.id
                    AND up.profile_type = N'VENDOR'
                INNER JOIN dbo.vendor_profiles vp ON vp.user_profile_id = up.id
                INNER JOIN dbo.categories c ON c.id = vp.category_id
                WHERE u.email = :email
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("email", email);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findVendorProducts(Long vendorProfileId) {
        String sql = """
                SELECT
                    id,
                    vendor_profile_id AS vendorProfileId,
                    name AS productName,
                    price AS productPrice,
                    short_description AS productSummary,
                    image_url AS productImageUrl,
                    status
                FROM dbo.vendor_products
                WHERE vendor_profile_id = :vendorProfileId
                  AND status = N'ACTIVE'
                ORDER BY id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("vendorProfileId", vendorProfileId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findVendorProduct(Long vendorProfileId, Long productId) {
        String sql = """
                SELECT
                    id,
                    vendor_profile_id AS vendorProfileId,
                    name AS productName,
                    price AS productPrice,
                    short_description AS productSummary,
                    image_url AS productImageUrl,
                    status
                FROM dbo.vendor_products
                WHERE id = :productId
                  AND vendor_profile_id = :vendorProfileId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("vendorProfileId", vendorProfileId);
        map.put("productId", productId);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Long> findActiveCategoryIdByName(String categoryName) {
        String sql = """
                SELECT TOP 1 id
                FROM dbo.categories
                WHERE name = :categoryName
                  AND is_active = 1
                ORDER BY id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("categoryName", categoryName);
        return namedParameterJdbcTemplate.queryForList(sql, map, Long.class).stream().findFirst();
    }

    public int updateVendorProfile(Long userId, Long vendorProfileId, Map<String, Object> profile) {
        String sql = """
                UPDATE up
                SET contact_name = :contactName,
                    contact_phone = :contactPhone,
                    contact_email = :contactEmail,
                    city = :city,
                    district = :district,
                    address = :address
                FROM dbo.user_profiles up
                INNER JOIN dbo.vendor_profiles vp ON vp.user_profile_id = up.id
                WHERE up.user_id = :userId
                  AND up.profile_type = N'VENDOR'
                  AND vp.id = :vendorProfileId;

                UPDATE dbo.vendor_profiles
                SET category_id = :categoryId,
                    brand_name = :brandName,
                    avatar_image_url = :avatarImageUrl,
                    cover_image_url = :coverImageUrl,
                    instagram_url = :instagramUrl,
                    facebook_url = :facebookUrl,
                    website_url = :websiteUrl,
                    brand_summary = :brandSummary,
                    brand_description = :brandDescription
                WHERE id = :vendorProfileId;
                """;

        Map<String, Object> map = new HashMap<>(profile);
        map.put("userId", userId);
        map.put("vendorProfileId", vendorProfileId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public Long createVendorProduct(Long vendorProfileId, Map<String, Object> product) {
        String sql = """
                INSERT INTO dbo.vendor_products (
                    vendor_profile_id,
                    name,
                    short_description,
                    description,
                    price,
                    image_url,
                    is_featured,
                    status
                )
                OUTPUT INSERTED.id
                VALUES (
                    :vendorProfileId,
                    :productName,
                    :productSummary,
                    NULL,
                    :productPrice,
                    :productImageUrl,
                    0,
                    N'ACTIVE'
                )
                """;

        Map<String, Object> map = new HashMap<>(product);
        map.put("vendorProfileId", vendorProfileId);
        return namedParameterJdbcTemplate.queryForObject(sql, map, Long.class);
    }

    public int updateVendorProduct(Long vendorProfileId, Long productId, Map<String, Object> product) {
        String sql = """
                UPDATE dbo.vendor_products
                SET name = :productName,
                    short_description = :productSummary,
                    price = :productPrice,
                    image_url = :productImageUrl,
                    status = N'ACTIVE'
                WHERE id = :productId
                  AND vendor_profile_id = :vendorProfileId
                """;

        Map<String, Object> map = new HashMap<>(product);
        map.put("vendorProfileId", vendorProfileId);
        map.put("productId", productId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int hideVendorProduct(Long vendorProfileId, Long productId) {
        String sql = """
                UPDATE dbo.vendor_products
                SET status = N'HIDDEN'
                WHERE id = :productId
                  AND vendor_profile_id = :vendorProfileId
                  AND status = N'ACTIVE'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("vendorProfileId", vendorProfileId);
        map.put("productId", productId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int bindApplicationDateSelectedStall(Long applicationId, LocalDate applyDate, Long stallId) {
        String sql = """
                UPDATE dbo.application_dates
                SET selected_stall_id = :stallId
                WHERE application_id = :applicationId
                  AND apply_date = :applyDate
                  AND selected_stall_id IS NULL
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        map.put("applyDate", applyDate);
        map.put("stallId", stallId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public Optional<Map<String, Object>> findSelectedStallApplication(String applicationNo, LocalDate applyDate, String stallNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    ad.id AS applicationDateId,
                    ad.selected_stall_id AS selectedStallId,
                    ad.apply_date AS applyDate,
                    s.stall_no AS stallNo
                FROM dbo.event_applications a
                INNER JOIN dbo.application_dates ad ON ad.application_id = a.id
                INNER JOIN dbo.event_stalls s ON s.id = ad.selected_stall_id
                WHERE a.application_no = :applicationNo
                  AND ad.apply_date = :applyDate
                  AND s.stall_no = :stallNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);
        map.put("applyDate", applyDate);
        map.put("stallNo", stallNo);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findSelectedApplicationDates(String applicationNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    ad.id AS applicationDateId,
                    ad.apply_date AS applyDate,
                    ad.selected_stall_id AS selectedStallId,
                    s.stall_no AS stallNo,
                    z.zone_name AS zoneName,
                    e.stall_width AS width,
                    e.stall_length AS length
                FROM dbo.event_applications a
                INNER JOIN dbo.application_dates ad ON ad.application_id = a.id
                INNER JOIN dbo.event_stalls s ON s.id = ad.selected_stall_id
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                LEFT JOIN dbo.event_stall_zones z ON z.id = s.zone_id
                WHERE a.application_no = :applicationNo
                ORDER BY ad.apply_date ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findVendorStallMapApplication(String applicationNo, LocalDate applyDate) {
        String sql = """
                SELECT
                    a.application_no AS applicationNo,
                    a.user_id AS userId,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.deposit_status AS depositStatus,
                    a.is_cancelled AS isCancelled,
                    ad_target.apply_date AS currentApplyDate,
                    ad_target.selected_stall_id AS selectedStallId,
                    selected_stall.stall_no AS selectedStallNo,
                    e.stall_width AS selectedStallWidth,
                    e.stall_length AS selectedStallLength,
                    selected_zone.zone_name AS selectedStallZoneName,
                    date_counts.applicationDateCount,
                    date_counts.selectedStallCount,
                    application_dates.applyDates,
                    vp.brand_name AS vendorName,
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.city,
                    e.district,
                    e.address,
                    e.start_at AS startAt,
                    e.end_at AS endAt,
                    e.end_at AS eventEndAt,
                    refund_data.refundStatus
                FROM dbo.event_applications a
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                LEFT JOIN dbo.application_dates ad_target ON ad_target.application_id = a.id
                    AND ad_target.apply_date = :applyDate
                LEFT JOIN dbo.event_stalls selected_stall ON selected_stall.id = ad_target.selected_stall_id
                LEFT JOIN dbo.event_stall_zones selected_zone ON selected_zone.id = selected_stall.zone_id
                OUTER APPLY (
                    SELECT STRING_AGG(CONVERT(varchar(10), ad.apply_date, 23), ',') WITHIN GROUP (ORDER BY ad.apply_date) AS applyDates
                    FROM dbo.application_dates ad
                    WHERE ad.application_id = a.id
                ) application_dates
                OUTER APPLY (
                    SELECT
                        COUNT(*) AS applicationDateCount,
                        SUM(CASE WHEN ad.selected_stall_id IS NULL THEN 0 ELSE 1 END) AS selectedStallCount
                    FROM dbo.application_dates ad
                    WHERE ad.application_id = a.id
                ) date_counts
                OUTER APPLY (
                    SELECT TOP 1 r.refund_status AS refundStatus
                    FROM dbo.refunds r
                    WHERE r.application_id = a.id
                    ORDER BY
                        CASE r.refund_status
                            WHEN N'REFUNDED' THEN 1
                            WHEN N'REFUNDING' THEN 2
                            WHEN N'REFUND_REQUESTED' THEN 3
                            WHEN N'REFUND_FAILED' THEN 4
                            ELSE 5
                        END,
                        r.id DESC
                ) refund_data
                WHERE a.application_no = :applicationNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);
        map.put("applyDate", applyDate);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findEventStallsMap(Long eventId, LocalDate applyDate) {
        String sql = """
                SELECT
                    s.id AS stallId,
                    s.zone_id AS zoneId,
                    z.zone_name AS zoneName,
                    s.stall_no AS stallNo,
                    e.stall_width AS width,
                    e.stall_length AS length,
                    CASE
                        WHEN s.status <> N'AVAILABLE' THEN s.status
                        WHEN selected_application.id IS NOT NULL THEN N'SELECTED'
                        ELSE N'AVAILABLE'
                    END AS status,
                    selected_application.id AS selectedApplicationId,
                    selected_vp.brand_name AS vendorName,
                    selected_category.name AS brandType,
                    selected_vendor.contact_name AS vendorOwnerName,
                    selected_at.selectedAt
                FROM dbo.event_stalls s
                INNER JOIN dbo.market_events e ON e.id = s.event_id
                INNER JOIN dbo.event_stall_zones z ON z.id = s.zone_id
                LEFT JOIN dbo.application_dates selected_date ON selected_date.selected_stall_id = s.id
                    AND selected_date.apply_date = :applyDate
                LEFT JOIN dbo.event_applications selected_application ON selected_application.id = selected_date.application_id
                    AND selected_application.is_cancelled = 0
                LEFT JOIN dbo.vendor_profiles selected_vp ON selected_vp.id = selected_application.vendor_profile_id
                LEFT JOIN dbo.categories selected_category ON selected_category.id = selected_vp.category_id
                LEFT JOIN dbo.user_profiles selected_vendor ON selected_vendor.id = selected_vp.user_profile_id
                OUTER APPLY (
                    SELECT TOP 1 rl.created_at AS selectedAt
                    FROM dbo.status_logs sl
                    INNER JOIN dbo.request_logs rl ON rl.id = sl.request_log_id
                    WHERE rl.status_code BETWEEN 200 AND 299
                      AND sl.target_type = N'APPLICATION_DATE'
                      AND sl.target_id = selected_date.id
                      AND sl.status_field = N'application_dates.selected_stall_id'
                      AND sl.new_status = CONVERT(NVARCHAR(100), s.id)
                    ORDER BY rl.created_at DESC, sl.id DESC
                ) selected_at
                WHERE s.event_id = :eventId
                ORDER BY z.zone_name ASC, s.stall_no ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("applyDate", applyDate);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findOrganizerStallMapEvent(Long organizerUserId, Long eventId) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.location_name AS locationName,
                    e.city,
                    e.district,
                    e.address,
                    e.start_at AS startAt,
                    e.end_at AS endAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    e.brands_public_at AS brandsPublicAt,
                    e.workflow_status AS workflowStatus,
                    e.map_image_url AS mapImageUrl,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) AS totalStallCount,
                    COALESCE(full_status.isFullySelected, 0) AS isFullySelected
                FROM dbo.market_events e
                OUTER APPLY (
                    SELECT COUNT(*) AS totalStalls
                    FROM dbo.event_stalls s
                    WHERE s.event_id = e.id
                ) stall_count
                OUTER APPLY (
                    SELECT CASE
                        WHEN COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) > 0
                         AND NOT EXISTS (
                            SELECT 1
                            FROM (
                                SELECT TOP (DATEDIFF(DAY, CONVERT(date, e.start_at), CONVERT(date, e.end_at)) + 1)
                                    DATEADD(DAY, ROW_NUMBER() OVER (ORDER BY object_id) - 1, CONVERT(date, e.start_at)) AS applyDate
                                FROM sys.all_objects
                            ) event_dates
                            OUTER APPLY (
                                SELECT COUNT(DISTINCT ad.selected_stall_id) AS selectedStallCount
                                FROM dbo.application_dates ad
                                INNER JOIN dbo.event_applications a ON a.id = ad.application_id
                                    AND a.event_id = e.id
                                    AND a.is_cancelled = 0
                                WHERE ad.apply_date = event_dates.applyDate
                                  AND ad.selected_stall_id IS NOT NULL
                            ) selected_count
                            WHERE COALESCE(selected_count.selectedStallCount, 0)
                                < COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths)
                         )
                        THEN 1 ELSE 0
                    END AS isFullySelected
                ) full_status
                WHERE e.id = :eventId
                  AND e.user_id = :organizerUserId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventId", eventId);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findEventForStallStatus(Long eventId) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.start_at AS startAt,
                    e.end_at AS endAt
                FROM dbo.market_events e
                WHERE e.id = :eventId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findOrganizerStallMapDetail(
            Long organizerUserId,
            Long eventId,
            String stallNo,
            LocalDate applyDate) {
        String sql = """
                SELECT
                    s.id AS stallId,
                    s.event_id AS eventId,
                    s.zone_id AS zoneId,
                    z.zone_name AS zoneName,
                    s.stall_no AS stallNo,
                    e.stall_width AS width,
                    e.stall_length AS length,
                    CASE
                        WHEN s.status <> N'AVAILABLE' THEN s.status
                        WHEN a.id IS NOT NULL THEN N'SELECTED'
                        ELSE N'AVAILABLE'
                    END AS stallStatus,
                    ad.id AS applicationDateId,
                    ad.apply_date AS applyDate,
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.deposit_status AS depositStatus,
                    a.is_cancelled AS isCancelled,
                    a.total_amount AS totalAmount,
                    a.created_at AS appliedAt,
                    selected_at.selectedAt,
                    vp.brand_name AS brandName,
                    up.contact_name AS vendorOwnerName,
                    up.contact_phone AS vendorPhone,
                    up.contact_email AS vendorEmail,
                    vp.brand_description AS brandDescription,
                    c.name AS categoryName,
                    refund_data.refundStatus
                FROM dbo.event_stalls s
                INNER JOIN dbo.event_stall_zones z ON z.id = s.zone_id
                INNER JOIN dbo.market_events e ON e.id = s.event_id
                LEFT JOIN dbo.application_dates ad ON ad.selected_stall_id = s.id
                    AND ad.apply_date = :applyDate
                LEFT JOIN dbo.event_applications a ON a.id = ad.application_id
                    AND a.is_cancelled = 0
                LEFT JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                LEFT JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                LEFT JOIN dbo.categories c ON c.id = vp.category_id
                OUTER APPLY (
                    SELECT TOP 1 r.refund_status AS refundStatus
                    FROM dbo.refunds r
                    WHERE r.application_id = a.id
                    ORDER BY
                        CASE r.refund_status
                            WHEN N'REFUNDED' THEN 1
                            WHEN N'REFUNDING' THEN 2
                            WHEN N'REFUND_REQUESTED' THEN 3
                            WHEN N'REFUND_FAILED' THEN 4
                            ELSE 5
                        END,
                        r.id DESC
                ) refund_data
                OUTER APPLY (
                    SELECT TOP 1 rl.created_at AS selectedAt
                    FROM dbo.status_logs sl
                    INNER JOIN dbo.request_logs rl ON rl.id = sl.request_log_id
                    WHERE rl.status_code BETWEEN 200 AND 299
                      AND sl.target_type = N'APPLICATION_DATE'
                      AND sl.target_id = ad.id
                      AND sl.status_field = N'application_dates.selected_stall_id'
                      AND sl.new_status = CONVERT(NVARCHAR(100), s.id)
                    ORDER BY rl.created_at DESC, sl.id DESC
                ) selected_at
                WHERE s.event_id = :eventId
                  AND s.stall_no = :stallNo
                  AND e.user_id = :organizerUserId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventId", eventId);
        map.put("stallNo", stallNo);
        map.put("applyDate", applyDate);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findEventStallsStatus(Long eventId, LocalDate applyDate) {
        String sql = """
                SELECT
                    s.id AS stallId,
                    s.event_id AS eventId,
                    s.zone_id AS zoneId,
                    z.zone_name AS zoneName,
                    s.stall_no AS stallNo,
                    e.stall_width AS width,
                    e.stall_length AS length,
                    CASE
                        WHEN s.status <> N'AVAILABLE' THEN s.status
                        WHEN a.id IS NOT NULL THEN N'SELECTED'
                        ELSE N'AVAILABLE'
                    END AS status,
                    vp.brand_name AS vendorName
                FROM dbo.event_stalls s
                INNER JOIN dbo.market_events e ON e.id = s.event_id
                INNER JOIN dbo.event_stall_zones z ON z.id = s.zone_id
                LEFT JOIN dbo.application_dates ad ON ad.selected_stall_id = s.id
                    AND ad.apply_date = :applyDate
                LEFT JOIN dbo.event_applications a ON a.id = ad.application_id
                    AND a.is_cancelled = 0
                LEFT JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                LEFT JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                WHERE s.event_id = :eventId
                ORDER BY z.zone_name ASC, s.stall_no ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("applyDate", applyDate);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }
}
