package com.example.demo.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizerRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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

    public Optional<Map<String, Object>> findOrganizerAccountByEmail(String email) {
        String sql = """
                SELECT
                    u.id AS userId,
                    u.role,
                    op.organizer_name AS organizerName,
                    up.contact_name AS contactName,
                    up.contact_phone AS contactPhone,
                    up.contact_email AS contactEmail,
                    op.company_name AS companyName,
                    op.tax_id AS taxId,
                    up.city,
                    up.district,
                    up.address,
                    op.service_days AS serviceDays,
                    op.service_start_time AS serviceStartTime,
                    op.service_end_time AS serviceEndTime
                FROM dbo.users u
                INNER JOIN dbo.user_profiles up ON up.user_id = u.id
                    AND up.profile_type = N'ORGANIZER'
                LEFT JOIN dbo.organizer_profiles op ON op.user_profile_id = up.id
                WHERE u.email = :email
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("email", email);

        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, map);
        return RepositoryResultMapper.normalizeOptional(list.stream().findFirst());
    }

    public int saveOrganizerProfile(Long organizerUserId, Map<String, Object> profile) {
        String sql = """
                UPDATE up
                SET contact_name = :contactName,
                    contact_phone = :contactPhone,
                    contact_email = :contactEmail,
                    city = :city,
                    district = :district,
                    address = :address
                FROM dbo.user_profiles up
                WHERE up.user_id = :organizerUserId
                  AND up.profile_type = N'ORGANIZER';

                INSERT INTO dbo.organizer_profiles (user_profile_id)
                SELECT up.id
                FROM dbo.user_profiles up
                WHERE up.user_id = :organizerUserId
                  AND up.profile_type = N'ORGANIZER'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.organizer_profiles existing_op
                      WHERE existing_op.user_profile_id = up.id
                  );

                UPDATE op
                SET organizer_name = :organizerName,
                    company_name = :companyName,
                    tax_id = :taxId,
                    service_days = :serviceDays,
                    service_start_time = :serviceStartTime,
                    service_end_time = :serviceEndTime
                FROM dbo.organizer_profiles op
                INNER JOIN dbo.user_profiles up ON up.id = op.user_profile_id
                WHERE up.user_id = :organizerUserId
                  AND up.profile_type = N'ORGANIZER';
                """;

        Map<String, Object> map = new HashMap<>(profile);
        map.put("organizerUserId", organizerUserId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public List<Map<String, Object>> findOrganizerAccountingEvents(
            Long organizerUserId,
            String eventTitle,
            LocalDateTime eventStartAt,
            LocalDateTime eventEndExclusive) {
        String sql = """
                WITH application_financial AS (
                    SELECT
                        a.id,
                        a.event_id,
                        a.payment_status,
                        a.is_cancelled,
                        a.deposit_amount,
                        a.deposit_status,
                        COALESCE(paid_payment.paidAmount,
                            CASE WHEN a.payment_status = N'PAID' THEN a.total_amount ELSE 0 END
                        ) AS paidAmount,
                        COALESCE(refunded.refundAmount, 0) AS refundAmount
                    FROM dbo.event_applications a
                    OUTER APPLY (
                        SELECT SUM(p.amount) AS paidAmount
                        FROM dbo.payments p
                        WHERE p.application_id = a.id
                          AND p.status = N'PAID'
                    ) paid_payment
                    OUTER APPLY (
                        SELECT SUM(r.amount) AS refundAmount
                        FROM dbo.refunds r
                        WHERE r.application_id = a.id
                          AND r.refund_status = N'REFUNDED'
                    ) refunded
                )
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.workflow_status AS publishStatus,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID' AND af.is_cancelled = 0 THEN 1
                        ELSE 0
                    END), 0) AS paidStallCount,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) AS totalStallCount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID' AND af.is_cancelled = 0 THEN af.paidAmount
                        ELSE 0
                    END), 0) AS grossRevenue,
                    COALESCE(SUM(af.refundAmount), 0) AS refundAmount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID'
                         AND af.is_cancelled = 0
                         AND af.deposit_status = N'RETURNED' THEN af.deposit_amount
                        ELSE 0
                    END), 0) AS returnedDepositAmount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID'
                         AND af.is_cancelled = 0
                         AND af.deposit_status = N'NOT_RETURNED' THEN af.deposit_amount
                        ELSE 0
                    END), 0) AS unreturnedDepositAmount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID' AND af.is_cancelled = 0 THEN af.paidAmount
                        ELSE 0
                    END), 0)
                    - COALESCE(SUM(af.refundAmount), 0)
                    - COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID'
                         AND af.is_cancelled = 0
                         AND af.deposit_status = N'RETURNED' THEN af.deposit_amount
                        ELSE 0
                    END), 0) AS netRevenue
                FROM dbo.market_events e
                OUTER APPLY (
                    SELECT COUNT(*) AS totalStalls
                    FROM dbo.event_stalls s
                    WHERE s.event_id = e.id
                ) stall_count
                LEFT JOIN application_financial af ON af.event_id = e.id
                WHERE e.user_id = :organizerUserId
                  AND (:eventTitle IS NULL OR e.title LIKE N'%' + :eventTitle + N'%')
                  AND (:eventStartAt IS NULL OR e.start_at >= :eventStartAt)
                  AND (:eventEndExclusive IS NULL OR e.end_at < :eventEndExclusive)
                GROUP BY
                    e.id,
                    e.title,
                    e.workflow_status,
                    e.start_at,
                    e.end_at,
                    e.registration_start_at,
                    e.registration_end_at,
                    e.max_booths,
                    stall_count.totalStalls
                ORDER BY
                    e.start_at DESC,
                    e.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventTitle", normalizeText(eventTitle));
        map.put("eventStartAt", eventStartAt);
        map.put("eventEndExclusive", eventEndExclusive);

        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findOrganizerAccountingEventDetail(Long organizerUserId, Long eventId) {
        String sql = """
                WITH application_financial AS (
                    SELECT
                        a.id,
                        a.event_id,
                        a.payment_status,
                        a.is_cancelled,
                        a.deposit_amount,
                        a.deposit_status,
                        COALESCE(paid_payment.paidAmount,
                            CASE WHEN a.payment_status = N'PAID' THEN a.total_amount ELSE 0 END
                        ) AS paidAmount,
                        COALESCE(refunded.refundAmount, 0) AS refundAmount,
                        latest_refund.refundStatus
                    FROM dbo.event_applications a
                    OUTER APPLY (
                        SELECT SUM(p.amount) AS paidAmount
                        FROM dbo.payments p
                        WHERE p.application_id = a.id
                          AND p.status = N'PAID'
                    ) paid_payment
                    OUTER APPLY (
                        SELECT SUM(r.amount) AS refundAmount
                        FROM dbo.refunds r
                        WHERE r.application_id = a.id
                          AND r.refund_status = N'REFUNDED'
                    ) refunded
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
                    ) latest_refund
                )
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.cover_image_url AS coverImageUrl,
                    e.location_name AS locationName,
                    e.city,
                    e.district,
                    e.address,
                    e.workflow_status AS publishStatus,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) AS totalStallCount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID' AND af.is_cancelled = 0 THEN 1
                        ELSE 0
                    END), 0) AS paidStallCount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status <> N'PAID' AND af.is_cancelled = 0 THEN 1
                        ELSE 0
                    END), 0) AS pendingPaymentStallCount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID' AND af.is_cancelled = 0 THEN af.paidAmount
                        ELSE 0
                    END), 0) AS grossRevenue,
                    COALESCE(SUM(af.refundAmount), 0) AS refundAmount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID'
                         AND af.is_cancelled = 0
                         AND af.deposit_status = N'RETURNED' THEN af.deposit_amount
                        ELSE 0
                    END), 0) AS returnedDepositAmount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID'
                         AND af.is_cancelled = 0
                         AND af.deposit_status = N'NOT_RETURNED' THEN af.deposit_amount
                        ELSE 0
                    END), 0) AS unreturnedDepositAmount,
                    COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID' AND af.is_cancelled = 0 THEN af.paidAmount
                        ELSE 0
                    END), 0)
                    - COALESCE(SUM(af.refundAmount), 0)
                    - COALESCE(SUM(CASE
                        WHEN af.payment_status = N'PAID'
                         AND af.is_cancelled = 0
                         AND af.deposit_status = N'RETURNED' THEN af.deposit_amount
                        ELSE 0
                    END), 0) AS netRevenue,
                    COALESCE(refund_stats.refundCount, 0) AS refundCount,
                    COALESCE(refund_stats.refundedCount, 0) AS refundedCount,
                    COALESCE(refund_stats.refundingCount, 0) AS refundingCount,
                    COALESCE(deposit_stats.returnedDepositCount, 0) AS returnedDepositCount,
                    COALESCE(deposit_stats.unreturnedDepositCount, 0) AS unreturnedDepositCount
                FROM dbo.market_events e
                OUTER APPLY (
                    SELECT COUNT(*) AS totalStalls
                    FROM dbo.event_stalls s
                    WHERE s.event_id = e.id
                ) stall_count
                OUTER APPLY (
                    SELECT
                        COUNT(*) AS refundCount,
                        SUM(CASE WHEN r.refund_status = N'REFUNDED' THEN 1 ELSE 0 END) AS refundedCount,
                        SUM(CASE WHEN r.refund_status IN (N'REFUND_REQUESTED', N'REFUNDING') THEN 1 ELSE 0 END) AS refundingCount
                    FROM dbo.refunds r
                    INNER JOIN dbo.event_applications a ON a.id = r.application_id
                    WHERE a.event_id = e.id
                ) refund_stats
                OUTER APPLY (
                    SELECT
                        SUM(CASE WHEN a.payment_status = N'PAID' AND a.is_cancelled = 0 AND a.deposit_status = N'RETURNED' THEN 1 ELSE 0 END) AS returnedDepositCount,
                        SUM(CASE WHEN a.payment_status = N'PAID' AND a.is_cancelled = 0 AND a.deposit_status = N'NOT_RETURNED' THEN 1 ELSE 0 END) AS unreturnedDepositCount
                    FROM dbo.event_applications a
                    WHERE a.event_id = e.id
                ) deposit_stats
                LEFT JOIN application_financial af ON af.event_id = e.id
                WHERE e.id = :eventId
                  AND e.user_id = :organizerUserId
                GROUP BY
                    e.id,
                    e.title,
                    e.cover_image_url,
                    e.location_name,
                    e.city,
                    e.district,
                    e.address,
                    e.workflow_status,
                    e.start_at,
                    e.end_at,
                    e.registration_start_at,
                    e.registration_end_at,
                    e.max_booths,
                    stall_count.totalStalls,
                    refund_stats.refundCount,
                    refund_stats.refundedCount,
                    refund_stats.refundingCount,
                    deposit_stats.returnedDepositCount,
                    deposit_stats.unreturnedDepositCount
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventId", eventId);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findOrganizerAccountingPaymentDetails(Long eventId) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.is_cancelled AS isCancelled,
                    a.deposit_amount AS depositAmount,
                    a.deposit_status AS depositStatus,
                    p.payment_no AS paymentNo,
                    p.amount AS paymentAmount,
                    p.status AS paymentStatus,
                    p.paid_at AS paidAt,
                    p.created_at AS paymentCreatedAt,
                    vp.id AS vendorProfileId,
                    vp.brand_name AS brandName,
                    vendor_up.contact_name AS contactName,
                    COALESCE(refund_data.refundAmount, 0) AS refundAmount,
                    refund_data.refundStatus
                FROM dbo.payments p
                INNER JOIN dbo.event_applications a ON a.id = p.application_id
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles vendor_up ON vendor_up.id = vp.user_profile_id
                OUTER APPLY (
                    SELECT
                        SUM(CASE WHEN r.refund_status = N'REFUNDED' THEN r.amount ELSE 0 END) AS refundAmount,
                        (
                            SELECT TOP 1 r2.refund_status
                            FROM dbo.refunds r2
                            WHERE r2.application_id = a.id
                            ORDER BY
                                CASE r2.refund_status
                                    WHEN N'REFUNDED' THEN 1
                                    WHEN N'REFUNDING' THEN 2
                                    WHEN N'REFUND_REQUESTED' THEN 3
                                    WHEN N'REFUND_FAILED' THEN 4
                                    ELSE 5
                                END,
                                r2.id DESC
                        ) AS refundStatus
                    FROM dbo.refunds r
                    WHERE r.application_id = a.id
                ) refund_data
                WHERE a.event_id = :eventId
                ORDER BY COALESCE(p.paid_at, p.created_at) DESC, p.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerStallEvents(
            Long organizerUserId,
            String eventTitle,
            LocalDateTime eventStartAt,
            LocalDateTime eventEndExclusive) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.cover_image_url AS coverImageUrl,
                    e.location_name AS locationName,
                    e.city,
                    e.district,
                    e.address,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    e.brands_public_at AS brandsPublicAt,
                    e.workflow_status AS workflowStatus,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) AS totalStallCount,
                    COALESCE(stall_selection.selectedStallCount, 0) AS selectedStallCount,
                    CASE
                        WHEN COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) - COALESCE(stall_selection.selectedStallCount, 0) < 0
                        THEN 0
                        ELSE COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) - COALESCE(stall_selection.selectedStallCount, 0)
                    END AS availableStallCount,
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
                OUTER APPLY (
                    SELECT MAX(COALESCE(selected_count.selectedStallCount, 0)) AS selectedStallCount
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
                ) stall_selection
                WHERE e.user_id = :organizerUserId
                  AND e.workflow_status IN (
                      N'READY_TO_PUBLISH',
                      N'PUBLISHED',
                      N'FINAL_REVIEW',
                      N'UNPUBLISH_REQUESTED',
                      N'UNPUBLISHED',
                      N'CANCELLED'
                  )
                  AND (:eventTitle IS NULL OR e.title LIKE N'%' + :eventTitle + N'%')
                  AND (:eventStartAt IS NULL OR e.start_at >= :eventStartAt)
                  AND (:eventEndExclusive IS NULL OR e.end_at < :eventEndExclusive)
                ORDER BY
                    e.start_at DESC,
                    e.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventTitle", normalizeText(eventTitle));
        map.put("eventStartAt", eventStartAt);
        map.put("eventEndExclusive", eventEndExclusive);

        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerEquipmentEvents(
            Long organizerUserId,
            String eventTitle,
            LocalDateTime eventStartAt,
            LocalDateTime eventEndExclusive) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.cover_image_url AS coverImageUrl,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    e.brands_public_at AS brandsPublicAt,
                    e.workflow_status AS workflowStatus,
                    COALESCE(application_stats.registeredStallCount, 0) AS registeredStallCount,
                    COALESCE(equipment_stats.freeEquipmentRentalCount, 0) AS freeEquipmentRentalCount,
                    COALESCE(equipment_stats.paidEquipmentRentalCount, 0) AS paidEquipmentRentalCount,
                    COALESCE(equipment_stats.freePowerRentalCount, 0) AS freePowerRentalCount,
                    COALESCE(equipment_stats.paidExtraPowerRentalCount, 0) AS paidExtraPowerRentalCount,
                    COALESCE(application_stats.vehicleRegistrationCount, 0) AS vehicleRegistrationCount,
                    COALESCE(NULLIF(stall_count.totalStalls, 0), e.max_booths) AS totalStallCount,
                    COALESCE(full_status.isFullySelected, 0) AS isFullySelected
                FROM dbo.market_events e
                OUTER APPLY (
                    SELECT COUNT(*) AS totalStalls
                    FROM dbo.event_stalls s
                    WHERE s.event_id = e.id
                ) stall_count
                OUTER APPLY (
                    SELECT
                        COUNT(*) AS registeredStallCount,
                        SUM(CASE
                            WHEN NULLIF(LTRIM(RTRIM(a.vehicle_no)), N'') IS NULL THEN 0
                            ELSE 1
                        END) AS vehicleRegistrationCount
                    FROM dbo.event_applications a
                    WHERE a.event_id = e.id
                      AND a.is_cancelled = 0
                ) application_stats
                OUTER APPLY (
                    SELECT
                        SUM(CASE
                            WHEN ee.item_type = N'EQUIPMENT' AND ee.charge_type = N'FREE' THEN er.quantity
                            ELSE 0
                        END) AS freeEquipmentRentalCount,
                        SUM(CASE
                            WHEN ee.item_type = N'EQUIPMENT' AND ee.charge_type = N'PAID' THEN er.quantity
                            ELSE 0
                        END) AS paidEquipmentRentalCount,
                        SUM(CASE
                            WHEN ee.item_type = N'POWER' AND ee.charge_type = N'FREE' THEN er.quantity
                            ELSE 0
                        END) AS freePowerRentalCount,
                        SUM(CASE
                            WHEN ee.item_type = N'POWER' AND ee.charge_type = N'PAID' THEN er.quantity
                            ELSE 0
                        END) AS paidExtraPowerRentalCount
                    FROM dbo.equipment_rentals er
                    INNER JOIN dbo.event_applications a ON a.id = er.application_id
                        AND a.event_id = e.id
                        AND a.is_cancelled = 0
                    INNER JOIN dbo.event_equipments ee ON ee.id = er.event_equipment_id
                ) equipment_stats
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
                WHERE e.user_id = :organizerUserId
                  AND (:eventTitle IS NULL OR e.title LIKE N'%' + :eventTitle + N'%')
                  AND (:eventStartAt IS NULL OR e.start_at >= :eventStartAt)
                  AND (:eventEndExclusive IS NULL OR e.end_at < :eventEndExclusive)
                ORDER BY
                    e.start_at DESC,
                    e.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventTitle", normalizeText(eventTitle));
        map.put("eventStartAt", eventStartAt);
        map.put("eventEndExclusive", eventEndExclusive);

        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerApplications(
            Long organizerUserId,
            String eventTitle,
            String brandName,
            LocalDateTime appliedStartAt,
            LocalDateTime appliedEndExclusive) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    e.id AS eventId,
                    e.title AS eventTitle,
                    CONCAT(
                        CONVERT(varchar(16), e.start_at, 120),
                        N' - ',
                        CONVERT(varchar(16), e.end_at, 120)
                    ) AS eventTime,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    vp.id AS vendorProfileId,
                    vp.brand_name AS vendorName,
                    vendor_up.contact_name AS vendorOwnerName,
                    a.created_at AS appliedAt,
                    application_dates.applyDates,
                    application_dates.applicationDateCount,
                    application_dates.selectedStallCount,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.deposit_status AS depositStatus,
                    a.is_cancelled AS isCancelled,
                    refund_data.refundStatus
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles vendor_up ON vendor_up.id = vp.user_profile_id
                OUTER APPLY (
                    SELECT
                        STRING_AGG(CONVERT(varchar(10), ad.apply_date, 23), ',') WITHIN GROUP (ORDER BY ad.apply_date) AS applyDates,
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
                WHERE e.user_id = :organizerUserId
                  AND e.workflow_status IN (
                      N'PUBLISHED',
                      N'FINAL_REVIEW',
                      N'UNPUBLISH_REQUESTED',
                      N'UNPUBLISHED'
                  )
                  AND (:eventTitle IS NULL OR e.title LIKE N'%' + :eventTitle + N'%')
                  AND (:brandName IS NULL OR vp.brand_name LIKE N'%' + :brandName + N'%')
                  AND (:appliedStartAt IS NULL OR a.created_at >= :appliedStartAt)
                  AND (:appliedEndExclusive IS NULL OR a.created_at < :appliedEndExclusive)
                ORDER BY
                    a.created_at DESC,
                    a.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("eventTitle", normalizeText(eventTitle));
        map.put("brandName", normalizeText(brandName));
        map.put("appliedStartAt", appliedStartAt);
        map.put("appliedEndExclusive", appliedEndExclusive);

        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findOrganizerApplicationDetail(Long organizerUserId, Long applicationId) {
        return findApplicationDetail(organizerUserId, applicationId, true);
    }

    public Optional<Map<String, Object>> findVendorApplicationDetail(Long vendorUserId, Long applicationId) {
        return findApplicationDetail(vendorUserId, applicationId, false);
    }

    private Optional<Map<String, Object>> findApplicationDetail(
            Long ownerUserId,
            Long applicationId,
            boolean organizerView) {
        String ownerCondition = organizerView
                ? "e.user_id = :ownerUserId"
                : "a.user_id = :ownerUserId";
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.event_id AS eventId,
                    e.title AS eventTitle,
                    e.summary AS eventSummary,
                    e.description AS eventDescription,
                    e.location_name AS locationName,
                    e.city AS eventCity,
                    e.district AS eventDistrict,
                    e.address AS eventAddress,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    e.base_fee AS baseFee,
                    e.cover_image_url AS eventCoverImageUrl,
                    vendor_user.id AS vendorUserId,
                    vendor_user.email AS vendorEmail,
                    vendor_up.id AS vendorUserProfileId,
                    vp.brand_name AS vendorName,
                    vendor_up.contact_name AS vendorOwnerName,
                    vendor_up.contact_phone AS vendorPhone,
                    vendor_up.contact_email AS vendorContactEmail,
                    vendor_up.city AS vendorCity,
                    vendor_up.district AS vendorDistrict,
                    vendor_up.address AS vendorAddress,
                    vp.id AS vendorProfileId,
                    vp.brand_description AS brandDescription,
                    vp.brand_summary AS brandSummary,
                    vp.instagram_url AS instagramUrl,
                    vp.facebook_url AS facebookUrl,
                    vp.website_url AS websiteUrl,
                    vp.avatar_image_url AS vendorAvatarUrl,
                    selected_stall_summary.selectedStallId,
                    selected_stall_summary.selectedStallNo,
                    selected_stall_summary.stallWidth,
                    selected_stall_summary.stallLength,
                    selected_stall_summary.stallZoneName,
                    a.vehicle_no AS vehicleNo,
                    a.applicant_note AS applicantNote,
                    a.total_amount AS totalAmount,
                    a.deposit_amount AS depositAmount,
                    a.deposit_status AS depositStatus,
                    a.payment_due_at AS paymentDueAt,
                    a.review_status AS reviewStatus,
                    review_note_summary.reviewNote,
                    review_note_summary.reviewNoteDetail,
                    a.payment_status AS paymentStatus,
                    a.is_cancelled AS isCancelled,
                    a.created_at AS appliedAt,
                    application_dates.applyDates,
                    application_dates.applicationDateCount,
                    application_dates.selectedStallCount,
                    latest_payment.paymentNo,
                    latest_payment.paymentAmount,
                    latest_payment.paymentProvider,
                    latest_payment.paymentProviderTradeNo,
                    latest_payment.paymentRecordStatus,
                    latest_payment.paidAt,
                    latest_payment.paymentCreatedAt,
                    latest_refund.refundNo,
                    latest_refund.refundAmount,
                    latest_refund.refundStatus,
                    latest_refund.refundedAt
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles vendor_up ON vendor_up.id = vp.user_profile_id
                INNER JOIN dbo.users vendor_user ON vendor_user.id = vendor_up.user_id
                OUTER APPLY (
                    SELECT TOP 1
                        ad.selected_stall_id AS selectedStallId,
                        selected_stall.stall_no AS selectedStallNo,
                        e.stall_width AS stallWidth,
                        e.stall_length AS stallLength,
                        selected_zone.zone_name AS stallZoneName
                    FROM dbo.application_dates ad
                    INNER JOIN dbo.event_stalls selected_stall ON selected_stall.id = ad.selected_stall_id
                    INNER JOIN dbo.event_stall_zones selected_zone ON selected_zone.id = selected_stall.zone_id
                    WHERE ad.application_id = a.id
                    ORDER BY ad.apply_date ASC
                ) selected_stall_summary
                OUTER APPLY (
                    SELECT
                        STRING_AGG(CONVERT(varchar(10), ad.apply_date, 23), ',') WITHIN GROUP (ORDER BY ad.apply_date) AS applyDates,
                        COUNT(*) AS applicationDateCount,
                        SUM(CASE WHEN ad.selected_stall_id IS NULL THEN 0 ELSE 1 END) AS selectedStallCount
                    FROM dbo.application_dates ad
                    WHERE ad.application_id = a.id
                ) application_dates
                OUTER APPLY (
                    SELECT TOP 1
                        arn.review_note AS reviewNote,
                        arn.review_note_detail AS reviewNoteDetail
                    FROM dbo.application_review_notes arn
                    WHERE arn.application_id = a.id
                    ORDER BY arn.created_at DESC, arn.id DESC
                ) review_note_summary
                OUTER APPLY (
                    SELECT TOP 1
                        p.payment_no AS paymentNo,
                        p.amount AS paymentAmount,
                        p.provider AS paymentProvider,
                        p.provider_trade_no AS paymentProviderTradeNo,
                        p.status AS paymentRecordStatus,
                        p.paid_at AS paidAt,
                        p.created_at AS paymentCreatedAt
                    FROM dbo.payments p
                    WHERE p.application_id = a.id
                    ORDER BY p.created_at DESC, p.id DESC
                ) latest_payment
                OUTER APPLY (
                    SELECT TOP 1
                        r.refund_no AS refundNo,
                        r.amount AS refundAmount,
                        r.refund_status AS refundStatus,
                        r.refunded_at AS refundedAt
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
                ) latest_refund
                WHERE a.id = :applicationId
                  AND %s
                """.formatted(ownerCondition);

        Map<String, Object> map = new HashMap<>();
        map.put("ownerUserId", ownerUserId);
        map.put("applicationId", applicationId);

        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public List<Map<String, Object>> findApplicationStatusLogs(Long applicationId) {
        String sql = """
                SELECT
                    sl.status_field AS statusField,
                    sl.new_status AS newStatus,
                    rl.created_at AS createdAt
                FROM dbo.status_logs sl
                INNER JOIN dbo.request_logs rl ON rl.id = sl.request_log_id
                WHERE (
                    sl.target_type = N'EVENT_APPLICATION'
                    AND sl.target_id = :applicationId
                )
                OR (
                    sl.target_type = N'APPLICATION_DATE'
                    AND sl.target_id IN (
                        SELECT ad.id
                        FROM dbo.application_dates ad
                        WHERE ad.application_id = :applicationId
                    )
                )
                ORDER BY rl.created_at ASC, sl.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findApplicationEquipmentRentals(Long applicationId) {
        String sql = """
                SELECT
                    er.id AS equipmentRentalId,
                    er.event_equipment_id AS eventEquipmentId,
                    er.equipment_name AS equipmentName,
                    ee.description AS equipmentDescription,
                    ee.charge_type AS chargeType,
                    ee.item_type AS itemType,
                    ee.wattage_limit AS wattageLimit,
                    er.rental_fee AS rentalFee,
                    er.pricing_unit AS pricingUnit,
                    er.quantity,
                    er.rental_units AS rentalUnits,
                    er.subtotal,
                    ra.id AS applianceId,
                    ra.appliance_name AS applianceName,
                    ra.wattage
                FROM dbo.equipment_rentals er
                INNER JOIN dbo.event_equipments ee ON ee.id = er.event_equipment_id
                LEFT JOIN dbo.rental_appliances ra ON ra.equipment_rental_id = er.id
                WHERE er.application_id = :applicationId
                ORDER BY er.id ASC, ra.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findApplicationDates(Long applicationId) {
        String sql = """
                SELECT
                    ad.id AS applicationDateId,
                    ad.apply_date AS applyDate,
                    ad.selected_stall_id AS selectedStallId,
                    s.stall_no AS stallNo,
                    z.zone_name AS zoneName,
                    e.stall_width AS width,
                    e.stall_length AS length
                FROM dbo.application_dates ad
                LEFT JOIN dbo.event_stalls s ON s.id = ad.selected_stall_id
                LEFT JOIN dbo.market_events e ON e.id = s.event_id
                LEFT JOIN dbo.event_stall_zones z ON z.id = s.zone_id
                WHERE ad.application_id = :applicationId
                ORDER BY ad.apply_date ASC, ad.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public Optional<Map<String, Object>> findOrganizerEquipmentEventDetail(Long organizerUserId, Long eventId) {
        String sql = """
                SELECT
                    e.id AS eventId,
                    e.title AS eventTitle,
                    e.location_name AS locationName,
                    e.city,
                    e.district,
                    e.address,
                    e.start_at AS eventStartAt,
                    e.end_at AS eventEndAt,
                    e.registration_start_at AS registrationStartAt,
                    e.registration_end_at AS registrationEndAt,
                    e.brands_public_at AS brandsPublicAt,
                    e.workflow_status AS workflowStatus,
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

    public List<Map<String, Object>> findEventEquipments(Long eventId) {
        String sql = """
                SELECT
                    ee.id AS eventEquipmentId,
                    ee.equipment_group_key AS equipmentGroupKey,
                    ee.name AS equipmentName,
                    ee.description AS equipmentDescription,
                    ee.rental_fee AS rentalFee,
                    ee.pricing_unit AS pricingUnit,
                    ee.charge_type AS chargeType,
                    ee.item_type AS itemType,
                    ee.unit,
                    ee.stock_quantity AS stockQuantity,
                    ee.per_stall_rental_limit AS perStallRentalLimit,
                    ee.rental_status AS rentalStatus,
                    ee.wattage_limit AS wattageLimit
                FROM dbo.event_equipments ee
                WHERE ee.event_id = :eventId
                ORDER BY ee.item_type ASC, ee.charge_type ASC, ee.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerEquipmentRentalStats(Long eventId) {
        String sql = """
                SELECT
                    ee.id AS eventEquipmentId,
                    ee.equipment_group_key AS equipmentGroupKey,
                    ee.name AS equipmentName,
                    ee.item_type AS itemType,
                    ee.charge_type AS chargeType,
                    ee.unit,
                    ee.stock_quantity AS stockQuantity,
                    ee.wattage_limit AS wattageLimit,
                    COALESCE(rental_stats.rentedQuantity, 0) AS rentedQuantity
                FROM dbo.event_equipments ee
                OUTER APPLY (
                    SELECT SUM(er.quantity) AS rentedQuantity
                    FROM dbo.equipment_rentals er
                    INNER JOIN dbo.event_applications a ON a.id = er.application_id
                        AND a.event_id = ee.event_id
                        AND a.is_cancelled = 0
                    WHERE er.event_equipment_id = ee.id
                ) rental_stats
                WHERE ee.event_id = :eventId
                ORDER BY ee.item_type ASC, ee.charge_type ASC, ee.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerEquipmentManagementRows(Long eventId) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    stall_summary.stallNo,
                    vp.brand_name AS brandName,
                    ee.id AS eventEquipmentId,
                    ee.name AS equipmentName,
                    er.quantity
                FROM dbo.event_applications a
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles vendor_up ON vendor_up.id = vp.user_profile_id
                INNER JOIN dbo.equipment_rentals er ON er.application_id = a.id
                INNER JOIN dbo.event_equipments ee ON ee.id = er.event_equipment_id
                    AND ee.item_type = N'EQUIPMENT'
                    AND ee.rental_status = N'ACTIVE'
                OUTER APPLY (
                    SELECT TOP 1 s.stall_no AS stallNo
                    FROM dbo.application_dates ad
                    INNER JOIN dbo.event_stalls s ON s.id = ad.selected_stall_id
                    WHERE ad.application_id = a.id
                    ORDER BY ad.apply_date ASC, ad.id ASC
                ) stall_summary
                WHERE a.event_id = :eventId
                  AND a.is_cancelled = 0
                ORDER BY stall_summary.stallNo ASC, a.id ASC, ee.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerPowerManagementRows(Long eventId) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    stall_summary.stallNo,
                    vp.brand_name AS brandName,
                    ee.id AS eventEquipmentId,
                    ee.name AS equipmentName,
                    ee.wattage_limit AS wattageLimit,
                    er.quantity
                FROM dbo.event_applications a
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles vendor_up ON vendor_up.id = vp.user_profile_id
                INNER JOIN dbo.equipment_rentals er ON er.application_id = a.id
                INNER JOIN dbo.event_equipments ee ON ee.id = er.event_equipment_id
                    AND ee.item_type = N'POWER'
                    AND ee.charge_type = N'PAID'
                OUTER APPLY (
                    SELECT TOP 1 s.stall_no AS stallNo
                    FROM dbo.application_dates ad
                    INNER JOIN dbo.event_stalls s ON s.id = ad.selected_stall_id
                    WHERE ad.application_id = a.id
                    ORDER BY ad.apply_date ASC, ad.id ASC
                ) stall_summary
                WHERE a.event_id = :eventId
                  AND a.is_cancelled = 0
                ORDER BY stall_summary.stallNo ASC, a.id ASC, ee.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public List<Map<String, Object>> findOrganizerVehicleManagementRows(Long eventId) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    stall_summary.stallNo,
                    vp.brand_name AS brandName,
                    vendor_up.contact_name AS contactName,
                    a.vehicle_no AS vehicleNo
                FROM dbo.event_applications a
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                INNER JOIN dbo.user_profiles vendor_up ON vendor_up.id = vp.user_profile_id
                OUTER APPLY (
                    SELECT TOP 1 s.stall_no AS stallNo
                    FROM dbo.application_dates ad
                    INNER JOIN dbo.event_stalls s ON s.id = ad.selected_stall_id
                    WHERE ad.application_id = a.id
                    ORDER BY ad.apply_date ASC, ad.id ASC
                ) stall_summary
                WHERE a.event_id = :eventId
                  AND a.is_cancelled = 0
                ORDER BY stall_summary.stallNo ASC, a.id ASC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, map));
    }

    public int updateApplicationReviewStatus(
            Long organizerUserId,
            Long applicationId,
            String reviewStatus) {
        String sql = """
                UPDATE a
                SET review_status = :reviewStatus
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events e ON e.id = a.event_id
                WHERE a.id = :applicationId
                  AND e.user_id = :organizerUserId
                  AND a.review_status = N'PENDING'
                  AND a.is_cancelled = 0
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("organizerUserId", organizerUserId);
        map.put("applicationId", applicationId);
        map.put("reviewStatus", reviewStatus);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int insertApplicationReviewNote(
            Long applicationId,
            String reviewNote,
            String reviewNoteDetail) {
        String sql = """
                INSERT INTO dbo.application_review_notes (
                    application_id,
                    review_note,
                    review_note_detail
                )
                VALUES (
                    :applicationId,
                    :reviewNote,
                    :reviewNoteDetail
                )
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        map.put("reviewNote", normalizeText(reviewNote));
        map.put("reviewNoteDetail", normalizeText(reviewNoteDetail));
        return namedParameterJdbcTemplate.update(sql, map);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
