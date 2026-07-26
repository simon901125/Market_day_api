package com.example.demo.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StallRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<Map<String, Object>> findVendorApplications(
            Long vendorUserId,
            String eventTitle,
            LocalDateTime eventStartAt,
            LocalDateTime eventEndExclusive) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.created_at AS appliedAt,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.deposit_status AS depositStatus,
                    a.is_cancelled AS isCancelled,
                    e.id AS eventId,
                    e.cover_image_url AS eventImageUrl,
                    e.title AS eventTitle,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    CONCAT(
                        CONVERT(varchar(10), e.start_at, 23),
                        N' - ',
                        CONVERT(varchar(10), e.end_at, 23)
                    ) AS eventDate,
                    CONCAT_WS(N' ',
                        NULLIF(LTRIM(RTRIM(e.city)), N''),
                        NULLIF(LTRIM(RTRIM(e.district)), N''),
                        NULLIF(LTRIM(RTRIM(e.location_name)), N'')
                    ) AS location,
                    application_dates.applicationDateCount,
                    application_dates.selectedStallCount,
                    refund_data.refundStatus
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                OUTER APPLY (
                    SELECT
                        COUNT(*) AS applicationDateCount,
                        SUM(CASE WHEN ad.selected_stall_id IS NULL THEN 0 ELSE 1 END) AS selectedStallCount
                    FROM dbo.application_dates ad
                    WHERE ad.application_id = a.id
                ) application_dates
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
                WHERE a.user_id = :vendorUserId
                  AND (:eventTitle IS NULL OR e.title LIKE N'%' + :eventTitle + N'%')
                  AND (:eventStartAt IS NULL OR e.start_at >= :eventStartAt)
                  AND (:eventEndExclusive IS NULL OR e.end_at < :eventEndExclusive)
                ORDER BY
                    a.created_at DESC,
                    a.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("vendorUserId", vendorUserId);
        map.put("eventTitle", normalizeText(eventTitle));
        map.put("eventStartAt", eventStartAt);
        map.put("eventEndExclusive", eventEndExclusive);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findApplicationForSelection(String applicationNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.event_id AS eventId,
                    e.title AS eventTitle,
                    e.user_id AS organizerUserId,
                    vp.brand_name AS brandName,
                    a.user_id AS userId,
                    a.vendor_profile_id AS vendorProfileId,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.is_cancelled AS isCancelled,
                    e.workflow_status AS workflowStatus,
                    CAST(CASE
                        WHEN e.workflow_status = N'PUBLISHED'
                         AND SYSDATETIME() <= e.registration_end_at
                        THEN 1 ELSE 0
                    END AS BIT) AS selectionOpen,
                    date_counts.applicationDateCount,
                    date_counts.selectedStallCount
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findVendorApplicationForCancellation(Long applicationId, Long vendorUserId) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.is_cancelled AS isCancelled,
                    e.user_id AS organizerUserId,
                    e.title AS eventTitle,
                    vp.brand_name AS brandName
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                WHERE a.id = :applicationId
                  AND a.user_id = :vendorUserId
                """;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("applicationId", applicationId);
        parameters.put("vendorUserId", vendorUserId);
        return RepositoryResultMapper.normalizeOptional(
                namedParameterJdbcTemplate.queryForList(sql, parameters).stream().findFirst());
    }

    public int cancelVendorApplication(Long applicationId, Long vendorUserId) {
        String sql = """
                UPDATE dbo.event_applications
                SET is_cancelled = 1
                WHERE id = :applicationId
                  AND user_id = :vendorUserId
                  AND is_cancelled = 0
                  AND (
                      review_status = N'PENDING'
                      OR (
                          review_status = N'APPROVED'
                          AND payment_status IN (N'PENDING', N'FAILED')
                      )
                  )
                """;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("applicationId", applicationId);
        parameters.put("vendorUserId", vendorUserId);
        return namedParameterJdbcTemplate.update(sql, parameters);
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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
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

    /**
     *
     */
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
                    vp.instagram_url AS instagramUrl,
                    vp.facebook_url AS facebookUrl,
                    vp.website_url AS websiteUrl,
                    vp.avatar_image_url AS avatarImageUrl,
                    vp.cover_image_url AS coverImageUrl,
                    vp.brand_description AS brandDescription,
                    vp.brand_summary AS brandSummary
                FROM dbo.users u
                INNER JOIN dbo.user_profiles up ON up.user_id = u.id
                    AND up.profile_type = N'VENDOR'
                INNER JOIN dbo.vendor_profiles vp ON vp.user_profile_id = up.id
                WHERE u.email = :email
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("email", email);

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    /**
     * Finds the account role and whether its vendor profile has been created.
     * The left joins intentionally keep first-login vendor accounts that do not
     * have user_profiles or vendor_profiles rows yet.
     */
    public Optional<Map<String, Object>> findVendorDashboardStatusByEmail(String email) {
        String sql = """
                SELECT
                    u.id AS userId,
                    u.role,
                    u.email,
                    up.id AS userProfileId,
                    vp.id AS vendorProfileId,
                    CAST(CASE WHEN vp.id IS NULL THEN 0 ELSE 1 END AS BIT) AS hasVendorProfile
                FROM dbo.users u
                LEFT JOIN dbo.user_profiles up ON up.user_id = u.id
                    AND up.profile_type = N'VENDOR'
                LEFT JOIN dbo.vendor_profiles vp ON vp.user_profile_id = up.id
                WHERE u.email = :email
                """;

        return RepositoryResultMapper.normalizeOptional(
                namedParameterJdbcTemplate.queryForList(sql, Map.of("email", email)).stream().findFirst());
    }

    public Optional<Map<String, Object>> findVendorDashboardProfileByEmail(String email) {
        String sql = """
                SELECT
                    u.id AS userId,
                    u.role,
                    u.email,
                    up.id AS userProfileId,
                    vp.id AS vendorProfileId,
                    vp.brand_name AS name,
                    up.contact_name AS contactName,
                    up.contact_phone AS contactPhone,
                    up.contact_email AS contactEmail,
                    up.city,
                    up.district,
                    up.address,
                    vp.avatar_image_url AS avatarImageUrl,
                    vp.cover_image_url AS coverImageUrl,
                    vp.brand_summary AS brandSummary,
                    vp.brand_description AS brandDescription
                FROM dbo.users u
                LEFT JOIN dbo.user_profiles up ON up.user_id = u.id
                    AND up.profile_type = N'VENDOR'
                LEFT JOIN dbo.vendor_profiles vp ON vp.user_profile_id = up.id
                WHERE u.email = :email
                """;

        Map<String, Object> parameters = Map.of("email", email);
        return RepositoryResultMapper.normalizeOptional(
                namedParameterJdbcTemplate.queryForList(sql, parameters).stream().findFirst());
    }

    public Map<String, Object> findVendorDashboardApplicationCounts(Long userId) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE
                        WHEN a.is_cancelled = 0
                         AND a.review_status = N'PENDING'
                        THEN 1 ELSE 0 END), 0) AS pendingReviewCount,
                    COALESCE(SUM(CASE
                        WHEN a.is_cancelled = 0
                         AND a.review_status = N'APPROVED'
                         AND a.payment_status IN (N'PENDING', N'FAILED')
                        THEN 1 ELSE 0 END), 0) AS pendingPaymentCount,
                    COALESCE(SUM(CASE
                        WHEN a.is_cancelled = 0
                         AND a.review_status = N'APPROVED'
                         AND a.payment_status = N'PAID'
                         AND COALESCE(application_dates.applicationDateCount, 0) > 0
                         AND COALESCE(application_dates.selectedStallCount, 0)
                             < application_dates.applicationDateCount
                        THEN 1 ELSE 0 END), 0) AS pendingStallSelectionCount,
                    COALESCE((
                        SELECT COUNT(DISTINCT refund.application_id)
                        FROM dbo.refunds refund
                        INNER JOIN dbo.event_applications refund_application
                            ON refund_application.id = refund.application_id
                        WHERE refund_application.user_id = :userId
                          AND refund.refund_status IN (N'REFUND_REQUESTED', N'REFUNDING')
                    ), 0) AS pendingRefundCount
                FROM dbo.event_applications a
                LEFT JOIN (
                    SELECT
                        ad.application_id AS applicationId,
                        COUNT_BIG(*) AS applicationDateCount,
                        SUM(CASE WHEN ad.selected_stall_id IS NULL THEN 0 ELSE 1 END) AS selectedStallCount
                    FROM dbo.application_dates ad
                    GROUP BY ad.application_id
                ) application_dates ON application_dates.applicationId = a.id
                WHERE a.user_id = :userId
                """;

        return RepositoryResultMapper.normalizeMap(
                namedParameterJdbcTemplate.queryForMap(sql, Map.of("userId", userId)));
    }

    public List<Map<String, Object>> findVendorProducts(Long vendorProfileId) {
        String sql = """
                SELECT
                    id,
                    vendor_profile_id AS vendorProfileId,
                    name AS productName,
                    price AS productPrice,
                    short_description AS productSummary,
                    image_url AS productImageUrl
                FROM dbo.vendor_products
                WHERE vendor_profile_id = :vendorProfileId
                ORDER BY id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("vendorProfileId", vendorProfileId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findActiveCategoriesByIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT id, name, slug
                FROM dbo.categories
                WHERE id IN (:categoryIds)
                  AND is_active = 1
                ORDER BY id ASC
                """;
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(
                sql, Map.of("categoryIds", categoryIds)));
    }

    public List<Map<String, Object>> findVendorCategoriesByProfileIds(List<Long> vendorProfileIds) {
        if (vendorProfileIds == null || vendorProfileIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT
                    vp.id AS vendorProfileId,
                    c.id,
                    c.name,
                    c.slug
                FROM dbo.vendor_profiles vp
                INNER JOIN dbo.categories c ON c.id = vp.category_id
                WHERE vp.id IN (:vendorProfileIds)
                ORDER BY vp.id, c.id
                """;
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(
                sql, Map.of("vendorProfileIds", vendorProfileIds)));
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
                    instagram_url = :instagramUrl,
                    facebook_url = :facebookUrl,
                    website_url = :websiteUrl,
                    avatar_image_url = COALESCE(:avatarImageUrl, avatar_image_url),
                    cover_image_url = COALESCE(:coverImageUrl, cover_image_url),
                    brand_summary = :brandSummary,
                    brand_description = :brandDescription
                WHERE id = :vendorProfileId;
                """;

        Map<String, Object> map = new HashMap<>(profile);
        map.put("userId", userId);
        map.put("vendorProfileId", vendorProfileId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public Long createVendorProfile(
            Long userId,
            Long userProfileId,
            Map<String, Object> profile) {
        Long resolvedUserProfileId = userProfileId;
        if (resolvedUserProfileId == null) {
            String createUserProfileSql = """
                    INSERT INTO dbo.user_profiles (
                        user_id, profile_type, contact_name, contact_phone,
                        contact_email, city, district, address
                    )
                    OUTPUT INSERTED.id
                    VALUES (
                        :userId, N'VENDOR', :contactName, :contactPhone,
                        :contactEmail, :city, :district, :address
                    )
                    """;
            Map<String, Object> userProfileParameters = new HashMap<>(profile);
            userProfileParameters.put("userId", userId);
            resolvedUserProfileId = namedParameterJdbcTemplate.queryForObject(
                    createUserProfileSql,
                    userProfileParameters,
                    Long.class);
        } else {
            String updateUserProfileSql = """
                    UPDATE dbo.user_profiles
                    SET contact_name = :contactName,
                        contact_phone = :contactPhone,
                        contact_email = :contactEmail,
                        city = :city,
                        district = :district,
                        address = :address
                    WHERE id = :userProfileId
                      AND user_id = :userId
                      AND profile_type = N'VENDOR'
                    """;
            Map<String, Object> userProfileParameters = new HashMap<>(profile);
            userProfileParameters.put("userId", userId);
            userProfileParameters.put("userProfileId", resolvedUserProfileId);
            if (namedParameterJdbcTemplate.update(updateUserProfileSql, userProfileParameters) == 0) {
                return null;
            }
        }

        String createVendorProfileSql = """
                SET NOCOUNT ON;

                -- vendor_profiles 有 AFTER INSERT Trigger，新增 ID 必須先輸出到暫存表。
                DECLARE @insertedVendorProfiles TABLE (id BIGINT);

                INSERT INTO dbo.vendor_profiles (
                    user_profile_id, category_id, brand_name,
                    instagram_url, facebook_url, website_url,
                    avatar_image_url, cover_image_url,
                    brand_summary, brand_description
                )
                OUTPUT INSERTED.id INTO @insertedVendorProfiles (id)
                VALUES (
                    :userProfileId, :categoryId, :brandName,
                    :instagramUrl, :facebookUrl, :websiteUrl,
                    :avatarImageUrl, :coverImageUrl,
                    :brandSummary, :brandDescription
                );

                SELECT id
                FROM @insertedVendorProfiles;
                """;
        Map<String, Object> vendorProfileParameters = new HashMap<>(profile);
        vendorProfileParameters.put("userProfileId", resolvedUserProfileId);
        return namedParameterJdbcTemplate.queryForObject(
                createVendorProfileSql,
                vendorProfileParameters,
                Long.class);
    }

    public int replaceVendorProducts(Long vendorProfileId, List<Map<String, Object>> products) {
        List<Long> retainedProductIds = products.stream()
                .map(product -> product.get("id"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .toList();

        if (retainedProductIds.isEmpty()) {
            namedParameterJdbcTemplate.update(
                    "DELETE FROM dbo.vendor_products WHERE vendor_profile_id = :vendorProfileId",
                    Map.of("vendorProfileId", vendorProfileId));
        } else {
            namedParameterJdbcTemplate.update(
                    """
                            DELETE FROM dbo.vendor_products
                            WHERE vendor_profile_id = :vendorProfileId
                              AND id NOT IN (:productIds)
                            """,
                    Map.of("vendorProfileId", vendorProfileId, "productIds", retainedProductIds));
        }

        String updateSql = """
                UPDATE dbo.vendor_products
                SET name = :productName,
                    short_description = :productSummary,
                    price = :productPrice,
                    image_url = COALESCE(:productImageUrl, image_url)
                WHERE id = :id
                  AND vendor_profile_id = :vendorProfileId
                """;

        String insertSql = """
                INSERT INTO dbo.vendor_products (
                    vendor_profile_id,
                    name,
                    short_description,
                    description,
                    price,
                    image_url
                )
                VALUES (
                    :vendorProfileId,
                    :productName,
                    :productSummary,
                    NULL,
                    :productPrice,
                    :productImageUrl
                )
                """;
        int savedRows = 0;
        for (Map<String, Object> product : products) {
            Map<String, Object> parameters = new HashMap<>(product);
            parameters.put("vendorProfileId", vendorProfileId);
            if (product.get("id") == null) {
                savedRows += namedParameterJdbcTemplate.update(insertSql, parameters);
            } else {
                savedRows += namedParameterJdbcTemplate.update(updateSql, parameters);
            }
        }
        return savedRows;
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

    public Optional<Map<String, Object>> findSelectedStallApplication(String applicationNo, LocalDate applyDate,
            String stallNo) {
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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
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
                    e.workflow_status AS workflowStatus,
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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
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
                    selected_vp.id AS vendorProfileId,
                    selected_vp.brand_name AS vendorName,
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
                    e.user_id AS organizerUserId,
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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findEventForStallStatus(Long eventId) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.start_at AS startAt,
                    e.end_at AS endAt
                FROM dbo.market_events e
                WHERE e.id = :eventId
                  AND e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                  AND e.brands_public_at IS NOT NULL
                  AND e.brands_public_at <= SYSDATETIME()
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
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
                    vp.id AS vendorProfileId,
                    vp.brand_name AS brandName,
                    up.contact_name AS vendorOwnerName,
                    up.contact_phone AS vendorPhone,
                    up.contact_email AS vendorEmail,
                    vp.brand_description AS brandDescription,
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

        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
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
                  AND e.workflow_status IN (N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED')
                  AND e.brands_public_at IS NOT NULL
                  AND e.brands_public_at <= SYSDATETIME()
                ORDER BY z.zone_name ASC, s.stall_no ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("applyDate", applyDate);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    // ------------------------ 攤主專區 API -----------------------

    /**
     * 處理Blank字串
     * 
     * @param value 任意參數
     * @return Null
     */
    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return blankToNull(value);
    }

    /**
     * 依攤主市集報名頁的篩選條件查詢 workflow_status 為 PUBLISHED 的活動。
     * 日期採「區間有交集」判斷，讓跨日活動不會因只查其中一天而被漏掉。
     */
    public List<Map<String, Object>> findMarkets(
            String keyword,
            String city,
            String district,
            String status,
            LocalDate eventStartAt,
            LocalDate eventEndAt) {
        StringBuilder sql = new StringBuilder("""
                        SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.summary,
                    e.location_name AS locationName,
                    e.city,
                    e.district,
                    e.address,
                    e.max_booths AS maxBooths,
                    e.start_at AS startAt,
                    e.end_at AS endAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    CASE
                        WHEN GETDATE() > e.registration_end_at THEN 0
                        ELSE DATEDIFF(DAY, CONVERT(date, GETDATE()), CONVERT(date, e.registration_end_at))
                    END AS registrationDaysRemaining,
                    e.base_fee AS baseFee,

                    op.organizer_name AS organizerName,

                    e.cover_image_url AS imageUrl,

                    N'OPEN' AS registrationStatus

                FROM dbo.market_events AS e

                LEFT JOIN dbo.user_profiles AS up
                    ON up.user_id = e.user_id
                   AND up.profile_type = N'ORGANIZER'

                LEFT JOIN dbo.organizer_profiles AS op
                    ON op.user_profile_id = up.id

                WHERE e.workflow_status IN (N'PUBLISHED', N'UNPUBLISH_REQUESTED')
                        AND GETDATE() >= e.registration_start_at
                        AND GETDATE() <= e.registration_end_at
                        AND EXISTS (
                            SELECT 1
                            FROM (
                                SELECT TOP (
                                    CASE
                                        WHEN DATEDIFF(DAY, CONVERT(date, e.start_at), CONVERT(date, e.end_at)) >= 0
                                        THEN DATEDIFF(DAY, CONVERT(date, e.start_at), CONVERT(date, e.end_at)) + 1
                                        ELSE 0
                                    END
                                )
                                    DATEADD(
                                        DAY,
                                        ROW_NUMBER() OVER (ORDER BY object_id) - 1,
                                        CONVERT(date, e.start_at)
                                    ) AS applyDate
                                FROM sys.all_objects
                            ) event_dates
                            OUTER APPLY (
                                SELECT COUNT(*) AS appliedStalls
                                FROM dbo.application_dates ad
                                INNER JOIN dbo.event_applications a
                                    ON a.id = ad.application_id
                                WHERE a.event_id = e.id
                                  AND a.is_cancelled = 0
                                  AND a.review_status <> N'REJECTED'
                                  AND ad.apply_date = event_dates.applyDate
                            ) occupied
                            OUTER APPLY (
                                SELECT COALESCE(NULLIF(COUNT(s.id), 0), e.max_booths, 0) AS totalStalls
                                FROM dbo.event_stalls s
                                WHERE s.event_id = e.id
                                  AND s.status <> N'DISABLED'
                            ) capacity
                            WHERE occupied.appliedStalls < capacity.totalStalls
                        )
                        """);

        Map<String, Object> params = new HashMap<>();
        String normalizedKeyword = blankToNull(keyword);
        if (normalizedKeyword != null) {
            sql.append(
                    " AND (e.title LIKE :keyword OR e.summary LIKE :keyword OR e.location_name LIKE :keyword OR e.address LIKE :keyword)");
            params.put("keyword", "%" + normalizedKeyword + "%");
        }
        if (blankToNull(city) != null) {
            sql.append(" AND e.city = :city");
            params.put("city", city.trim());
        }
        if (blankToNull(district) != null) {
            sql.append(" AND e.district = :district");
            params.put("district", district.trim());
        }
        if (eventStartAt != null) {
            sql.append(" AND e.end_at >= :eventStartAt");
            params.put("eventStartAt", eventStartAt);
        }
        if (eventEndAt != null) {
            sql.append(" AND e.start_at <= :eventEndAt");
            params.put("eventEndAt", eventEndAt);
        }

        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null && !"ALL".equalsIgnoreCase(normalizedStatus)
                && !"全部狀態".equals(normalizedStatus)) {
            switch (normalizedStatus.toUpperCase()) {
                case "OPEN", "FULL" -> {
                    // 滿額狀態需依每日剩餘攤位計算，由 Service 組合資料後篩選。
                }
                default -> sql.append(" AND 1 = 0"); // 未知狀態不應意外回傳全部資料。
            }
        }

        sql.append(" ORDER BY e.start_at ASC, e.id DESC");
        return RepositoryResultMapper.normalizeList(
                namedParameterJdbcTemplate.queryForList(sql.toString(), params));
    }

    /**
     * 取得已發布活動的基本資料、分類及主辦方聯絡資訊
     * 
     * @param eventId 所選擇的活動ID
     * @return
     */
    public Optional<Map<String, Object>> findPublishedMarketDetail(Long eventId) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.summary,
                    e.description,
                    e.location_name AS locationName,
                    e.city,
                    e.district,
                    e.address,
                    e.start_at AS startAt,
                    e.end_at AS endAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    CASE
                        WHEN GETDATE() > e.registration_end_at THEN 0
                        ELSE DATEDIFF(DAY, CONVERT(date, GETDATE()), CONVERT(date, e.registration_end_at))
                    END AS registrationDaysRemaining,
                    e.max_booths AS maxBooths,
                    e.base_fee AS baseFee,
                    e.deposit_amount AS depositAmount,
                    e.cover_image_url AS coverImageUrl,
                    e.map_image_url AS mapImageUrl,
                    op.organizer_name AS organizerName,
                    op.company_name AS companyName,
                    op.service_days AS serviceDays,
                    op.service_start_time AS serviceStartTime,
                    op.service_end_time AS serviceEndTime,
                    up.contact_name AS contactName,
                    up.contact_phone AS contactPhone,
                    up.contact_email AS contactEmail,
                    e.stall_width AS stallWidth,
                    e.stall_length AS stallLength,
                    NULL AS stallHeight,
                    N'OPEN' AS registrationStatus
                FROM dbo.market_events e
                LEFT JOIN dbo.user_profiles up
                    ON up.user_id = e.user_id AND up.profile_type = N'ORGANIZER'
                LEFT JOIN dbo.organizer_profiles op ON op.user_profile_id = up.id
                WHERE e.id = :eventId
                  AND e.workflow_status IN (N'PUBLISHED', N'UNPUBLISH_REQUESTED')
                  AND GETDATE() >= e.registration_start_at
                  AND GETDATE() <= e.registration_end_at
                """;
        return RepositoryResultMapper.normalizeOptional(
                namedParameterJdbcTemplate.queryForList(sql, Map.of("eventId", eventId)).stream().findFirst());
    }

    public List<Map<String, Object>> findEventCategoriesByEventIds(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                SELECT
                    mec.event_id AS eventId,
                    c.id,
                    c.name,
                    c.slug
                FROM dbo.market_event_categories mec
                INNER JOIN dbo.categories c ON c.id = mec.category_id
                WHERE mec.event_id IN (:eventIds)
                ORDER BY mec.event_id, c.id
                """;
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(
                sql, Map.of("eventIds", eventIds)));
    }

    public List<Map<String, Object>> findMarketDailyAvailabilities(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                WITH event_dates AS (
                    SELECT
                        e.id AS eventId,
                        CONVERT(date, e.start_at) AS applyDate,
                        CONVERT(date, e.end_at) AS endDate,
                        e.max_booths AS maxBooths
                    FROM dbo.market_events e
                    WHERE e.id IN (:eventIds)

                    UNION ALL

                    SELECT
                        eventId,
                        DATEADD(DAY, 1, applyDate),
                        endDate,
                        maxBooths
                    FROM event_dates
                    WHERE applyDate < endDate
                )
                SELECT
                    d.eventId,
                    d.applyDate,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), d.maxBooths) AS totalStalls,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), d.maxBooths)
                        - COUNT(DISTINCT selected_stall.id) AS remainingStalls
                FROM event_dates d
                OUTER APPLY (
                    SELECT COUNT(*) AS totalStalls
                    FROM dbo.event_stalls s
                    WHERE s.event_id = d.eventId
                      AND s.status <> N'DISABLED'
                ) stall_count
                LEFT JOIN dbo.application_dates ad ON ad.apply_date = d.applyDate
                LEFT JOIN dbo.event_applications a
                    ON a.id = ad.application_id
                    AND a.event_id = d.eventId
                    AND a.is_cancelled = 0
                    AND a.review_status <> N'REJECTED'
                LEFT JOIN dbo.event_stalls selected_stall
                    ON selected_stall.id = ad.selected_stall_id
                    AND selected_stall.event_id = d.eventId
                    AND a.id IS NOT NULL
                GROUP BY d.eventId, d.applyDate, d.maxBooths, stall_count.totalStalls
                ORDER BY d.eventId, d.applyDate
                OPTION (MAXRECURSION 366)
                """;
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(
                sql, Map.of("eventIds", eventIds)));
    }

    /**
     * 逐日計算活動可用攤位數；有效且未被拒絕的報名日期會立即占用一個名額。
     * 
     * @param eventId 所選擇的活動ID
     * @return
     */
    public List<Map<String, Object>> findMarketDailyAvailability(Long eventId) {
        String sql = """
                WITH event_dates AS (
                    SELECT
                        CAST(start_at AS DATE) AS applyDate,
                        CAST(end_at AS DATE) AS endDate,
                        max_booths AS maxBooths
                    FROM dbo.market_events
                    WHERE id = :eventId
                    UNION ALL
                    SELECT DATEADD(DAY, 1, applyDate), endDate, maxBooths
                    FROM event_dates
                    WHERE applyDate < endDate
                ),
                capacity AS (
                    SELECT COALESCE(NULLIF(COUNT(s.id), 0), MAX(e.max_booths), 0) AS totalStalls
                    FROM dbo.market_events e
                    LEFT JOIN dbo.event_stalls s
                        ON s.event_id = e.id
                        AND s.status <> N'DISABLED'
                    WHERE e.id = :eventId
                )
                SELECT
                    d.applyDate,
                    capacity.totalStalls,
                    CASE
                        WHEN capacity.totalStalls - occupied.appliedStalls < 0 THEN 0
                        ELSE capacity.totalStalls - occupied.appliedStalls
                    END AS remainingStalls
                FROM event_dates d
                CROSS JOIN capacity
                OUTER APPLY (
                    SELECT COUNT(*) AS appliedStalls
                    FROM dbo.application_dates ad
                    INNER JOIN dbo.event_applications a
                        ON a.id = ad.application_id
                    WHERE a.event_id = :eventId
                      AND a.is_cancelled = 0
                      AND a.review_status <> N'REJECTED'
                      AND ad.apply_date = d.applyDate
                ) occupied
                ORDER BY d.applyDate
                OPTION (MAXRECURSION 366)
                """;
        return RepositoryResultMapper.normalizeList(
                namedParameterJdbcTemplate.queryForList(sql, Map.of("eventId", eventId)));
    }

    /**
     * 鎖定活動資料列直到目前交易結束，讓同一活動的報名容量檢查與新增依序執行。
     */
    public void lockMarketForApplication(Long eventId) {
        String sql = """
                SELECT id
                FROM dbo.market_events WITH (UPDLOCK, HOLDLOCK)
                WHERE id = :eventId
                """;
        namedParameterJdbcTemplate.queryForObject(sql, Map.of("eventId", eventId), Long.class);
    }

    /**
     * 找出有效報名數已達每日攤位容量的日期。
     */
    public List<LocalDate> findUnavailableApplicationDates(Long eventId, List<LocalDate> applyDates) {
        if (applyDates == null || applyDates.isEmpty()) {
            return List.of();
        }

        String sql = """
                WITH event_dates AS (
                    SELECT CAST(start_at AS DATE) AS applyDate, CAST(end_at AS DATE) AS endDate
                    FROM dbo.market_events
                    WHERE id = :eventId
                    UNION ALL
                    SELECT DATEADD(DAY, 1, applyDate), endDate
                    FROM event_dates
                    WHERE applyDate < endDate
                ),
                capacity AS (
                    SELECT COALESCE(NULLIF(COUNT(s.id), 0), MAX(e.max_booths), 0) AS totalStalls
                    FROM dbo.market_events e
                    LEFT JOIN dbo.event_stalls s
                        ON s.event_id = e.id
                        AND s.status <> N'DISABLED'
                    WHERE e.id = :eventId
                )
                SELECT d.applyDate
                FROM event_dates d
                CROSS JOIN capacity
                OUTER APPLY (
                    SELECT COUNT(*) AS appliedStalls
                    FROM dbo.application_dates ad
                    INNER JOIN dbo.event_applications a
                        ON a.id = ad.application_id
                    WHERE a.event_id = :eventId
                      AND a.is_cancelled = 0
                      AND a.review_status <> N'REJECTED'
                      AND ad.apply_date = d.applyDate
                ) occupied
                WHERE d.applyDate IN (:applyDates)
                  AND occupied.appliedStalls >= capacity.totalStalls
                ORDER BY d.applyDate
                OPTION (MAXRECURSION 366)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("applyDates", applyDates);
        return namedParameterJdbcTemplate.queryForList(sql, params, LocalDate.class);
    }

    /**
     * 取得活動目前啟用中的免費設備、租借設備及用電方案
     * 
     * @param eventId 所選擇的活動ID
     * @return
     */
    public List<Map<String, Object>> findPublishedMarketEquipments(Long eventId) {
        String sql = """
                SELECT
                    id AS eventEquipmentId,
                    equipment_group_key AS equipmentGroupKey,
                    name,
                    description,
                    rental_fee AS rentalFee,
                    pricing_unit AS pricingUnit,
                    unit,
                    charge_type AS chargeType,
                    item_type AS itemType,
                    stock_quantity AS stockQuantity,
                    per_stall_rental_limit AS perStallRentalLimit,
                    wattage_limit AS wattageLimit
                FROM dbo.event_equipments
                WHERE event_id = :eventId AND rental_status = N'ACTIVE'
                ORDER BY item_type, charge_type, id
                """;
        return RepositoryResultMapper.normalizeList(
                namedParameterJdbcTemplate.queryForList(sql, Map.of("eventId", eventId)));
    }

    /** 取得活動的交通方式說明。 */
    public List<Map<String, Object>> findPublishedMarketTrafficInfos(Long eventId) {
        String sql = """
                SELECT
                    traffic_values.sortOrder AS id,
                    traffic_values.trafficTitle,
                    traffic_values.trafficDetails
                FROM dbo.market_events e
                CROSS APPLY (VALUES
                    (1, N'開車', e.traffic_info_driving),
                    (2, N'公車', e.traffic_info_bus),
                    (3, N'捷運', e.traffic_info_metro)
                ) AS traffic_values(sortOrder, trafficTitle, trafficDetails)
                WHERE e.id = :eventId
                  AND NULLIF(LTRIM(RTRIM(traffic_values.trafficDetails)), N'') IS NOT NULL
                ORDER BY traffic_values.sortOrder
                """;
        return RepositoryResultMapper.normalizeList(
                namedParameterJdbcTemplate.queryForList(sql, Map.of("eventId", eventId)));
    }

    /**
     * 查詢攤主報名前需要驗證的活動資料。
     */
    public Optional<Map<String, Object>> findMarketEventForApplication(Long eventId) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.user_id AS organizerUserId,
                    e.title AS eventTitle,
                    e.start_at AS startAt,
                    e.end_at AS endAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    e.workflow_status AS workflowStatus,
                    e.base_fee AS baseFee,
                    e.deposit_amount AS depositAmount
                FROM dbo.market_events e
                WHERE e.id = :eventId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    /**
     * 同一個品牌在同一活動只能建立一筆報名資料
     * 
     * @param eventId
     * @param vendorProfileId
     * @return
     */
    public boolean existsVendorApplication(Long eventId, Long vendorProfileId) {
        String sql = """
                SELECT COUNT(1)
                FROM dbo.event_applications
                WHERE event_id = :eventId
                  AND vendor_profile_id = :vendorProfileId
                  AND is_cancelled = 0
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("vendorProfileId", vendorProfileId);
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, map, Integer.class);
        return count != null && count > 0;
    }

    /**
     * 查詢活動可租借設備，限制設備必須屬於目前報名活動
     * 
     * @param eventId
     * @param eventEquipmentId
     * @return
     */
    public Optional<Map<String, Object>> findEventEquipmentForApplication(Long eventId, Long eventEquipmentId) {
        String sql = """
                SELECT
                    ee.id AS eventEquipmentId,
                    ee.event_id AS eventId,
                    ee.name,
                    ee.rental_fee AS rentalFee,
                    ee.pricing_unit AS pricingUnit,
                    ee.unit,
                    ee.charge_type AS chargeType,
                    ee.item_type AS itemType,
                    ee.stock_quantity AS stockQuantity,
                    ee.per_stall_rental_limit AS perStallRentalLimit,
                    ee.rental_status AS rentalStatus,
                    ee.wattage_limit AS wattageLimit
                FROM dbo.event_equipments ee
                WHERE ee.event_id = :eventId
                  AND ee.id = :eventEquipmentId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("eventEquipmentId", eventEquipmentId);
        return RepositoryResultMapper
                .normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    /**
     * 取得同一活動日期前綴下最後一筆申請單號，用來產生下一個流水號。
     */
    public Optional<String> findLatestApplicationNoByPrefix(String prefix) {
        String sql = """
                SELECT TOP 1 application_no
                FROM dbo.event_applications
                WHERE application_no LIKE :prefix + N'%'
                ORDER BY application_no DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("prefix", prefix);
        return namedParameterJdbcTemplate.queryForList(sql, map, String.class).stream().findFirst();
    }

    /**
     * 新增活動報名主檔，回傳 event_applications.id。
     */
    public Long createEventApplication(
            String applicationNo,
            Long eventId,
            Long userId,
            Long vendorProfileId,
            String vehicleNo,
            String applicantNote,
            BigDecimal totalAmount,
            BigDecimal depositAmount,
            LocalDateTime paymentDueAt) {
        String sql = """
                INSERT INTO dbo.event_applications (
                    application_no,
                    event_id,
                    user_id,
                    vendor_profile_id,
                    vehicle_no,
                    applicant_note,
                    total_amount,
                    deposit_amount,
                    payment_due_at
                )
                OUTPUT INSERTED.id
                VALUES (
                    :applicationNo,
                    :eventId,
                    :userId,
                    :vendorProfileId,
                    :vehicleNo,
                    :applicantNote,
                    :totalAmount,
                    :depositAmount,
                    :paymentDueAt
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicationNo", applicationNo)
                .addValue("eventId", eventId)
                .addValue("userId", userId)
                .addValue("vendorProfileId", vendorProfileId)
                .addValue("vehicleNo", vehicleNo)
                .addValue("applicantNote", applicantNote)
                .addValue("totalAmount", totalAmount)
                .addValue("depositAmount", depositAmount)
                .addValue("paymentDueAt", paymentDueAt);
        Long applicationId = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
        if (applicationId == null || applicationId <= 0) {
            throw new IllegalStateException("Event application was created without a valid generated id");
        }
        return applicationId;
    }

    /**
     * 新增加購用電時的電器明細。
     */
    public void createRentalAppliance(Long equipmentRentalId, String applianceName, Integer wattage) {
        String sql = """
                INSERT INTO dbo.rental_appliances (
                    equipment_rental_id,
                    appliance_name,
                    wattage
                )
                VALUES (
                    :equipmentRentalId,
                    :applianceName,
                    :wattage
                )
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("equipmentRentalId", equipmentRentalId);
        map.put("applianceName", applianceName);
        map.put("wattage", wattage);
        namedParameterJdbcTemplate.update(sql, map);
    }

    /**
     * 新增單一報名日期；每個日期後續都可以獨立選位。
     */
    public void createApplicationDate(Long applicationId, LocalDate applyDate) {
        String sql = """
                INSERT INTO dbo.application_dates (
                    application_id,
                    apply_date
                )
                VALUES (
                    :applicationId,
                    :applyDate
                )
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        map.put("applyDate", applyDate);
        namedParameterJdbcTemplate.update(sql, map);
    }

    /**
     * 新增設備租借資料，保留報名當下的設備名稱與價格快照。
     */
    public Long createEquipmentRental(
            Long applicationId,
            Long eventEquipmentId,
            String equipmentName,
            BigDecimal rentalFee,
            String pricingUnit,
            String unit,
            Integer quantity,
            Integer rentalUnits,
            BigDecimal subtotal) {
        String sql = """
                INSERT INTO dbo.equipment_rentals (
                    application_id,
                    event_equipment_id,
                    equipment_name,
                    rental_fee,
                    pricing_unit,
                    unit,
                    quantity,
                    rental_units,
                    subtotal
                )
                VALUES (
                    :applicationId,
                    :eventEquipmentId,
                    :equipmentName,
                    :rentalFee,
                    :pricingUnit,
                    :unit,
                    :quantity,
                    :rentalUnits,
                    :subtotal
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("applicationId", applicationId)
                .addValue("eventEquipmentId", eventEquipmentId)
                .addValue("equipmentName", equipmentName)
                .addValue("rentalFee", rentalFee)
                .addValue("pricingUnit", pricingUnit)
                .addValue("unit", unit)
                .addValue("quantity", quantity)
                .addValue("rentalUnits", rentalUnits)
                .addValue("subtotal", subtotal);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[] { "id" });
        return keyHolder.getKey().longValue();
    }
}
