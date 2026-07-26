package com.example.demo.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public Optional<Map<String, Object>> findVendorPaymentUserByEmail(String email) {
        String sql = """
                SELECT
                    id AS userId,
                    email,
                    role,
                    status,
                    isLogin,
                    expired_time AS expiredTime
                FROM dbo.users
                WHERE email = :email
                  AND role = 'VENDOR'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findPayableApplication(String applicationNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.user_id AS userId,
                    a.total_amount AS totalAmount,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.payment_due_at AS paymentDueAt,
                    a.is_cancelled AS isCancelled,
                    me.title AS eventName,
                    me.payment_account_id AS paymentAccountId,
                    opa.organizer_profile_id AS paymentOrganizerProfileId,
                    opa.merchant_id AS merchantId,
                    opa.hash_key_encrypted AS hashKeyEncrypted,
                    opa.hash_iv_encrypted AS hashIvEncrypted,
                    opa.status AS paymentAccountStatus,
                    opa.verification_status AS paymentAccountVerificationStatus
                    FROM dbo.event_applications a
                    INNER JOIN dbo.market_events me ON me.id = a.event_id
                    LEFT JOIN dbo.organizer_payment_accounts opa
                        ON opa.id = me.payment_account_id
                    WHERE a.application_no = :applicationNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findRefundableApplication(String applicationNo) {
        String sql = """
                SELECT
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.user_id AS userId,
                    a.deposit_amount AS depositAmount,
                    a.review_status AS reviewStatus,
                    a.payment_status AS paymentStatus,
                    a.is_cancelled AS isCancelled,
                    me.id AS eventId,
                    me.user_id AS organizerUserId,
                    me.title AS eventName
                FROM dbo.event_applications a
                INNER JOIN dbo.market_events me ON me.id = a.event_id
                WHERE a.application_no = :applicationNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findPaymentStatusByApplicationNo(String applicationNo) {
        String sql = """
                SELECT TOP 1
                    a.id AS applicationId,
                    a.application_no AS applicationNo,
                    a.user_id AS userId,
                    a.total_amount AS applicationAmount,
                    a.review_status AS reviewStatus,
                    a.payment_status AS applicationPaymentStatus,
                    a.payment_due_at AS paymentDueAt,
                    a.is_cancelled AS isCancelled,
                    p.id AS paymentId,
                    p.payment_no AS paymentNo,
                    p.amount AS paymentAmount,
                    p.provider,
                    p.provider_trade_no AS providerTradeNo,
                    p.status AS paymentRecordStatus,
                    p.paid_at AS paidAt,
                    p.created_at AS paymentCreatedAt
                FROM dbo.event_applications a
                LEFT JOIN dbo.payments p ON p.application_id = a.id
                WHERE a.application_no = :applicationNo
                ORDER BY p.created_at DESC, p.id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationNo", applicationNo);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findLatestPendingPayment(Long applicationId) {
        String sql = """
                SELECT TOP 1
                    id AS paymentId,
                    payment_no AS paymentNo,
                    application_id AS applicationId,
                    amount,
                    provider,
                    provider_trade_no AS providerTradeNo,
                    payment_account_id AS paymentAccountId,
                    status,
                    paid_at AS paidAt,
                    created_at AS createdAt
                FROM dbo.payments
                WHERE application_id = :applicationId
                  AND status = N'PENDING'
                ORDER BY created_at DESC, id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findLatestPaidPayment(Long applicationId) {
        String sql = """
                SELECT TOP 1
                    id AS paymentId,
                    payment_no AS paymentNo,
                    application_id AS applicationId,
                    amount,
                    provider,
                    provider_trade_no AS providerTradeNo,
                    status,
                    paid_at AS paidAt,
                    created_at AS createdAt
                FROM dbo.payments
                WHERE application_id = :applicationId
                  AND status = N'PAID'
                ORDER BY paid_at DESC, created_at DESC, id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findLatestRefundByApplicationId(Long applicationId) {
        String sql = """
                SELECT TOP 1
                    id AS refundId,
                    refund_no AS refundNo,
                    application_id AS applicationId,
                    payment_id AS paymentId,
                    amount,
                    reason,
                    refund_status AS refundStatus,
                    refunded_at AS refundedAt
                FROM dbo.refunds
                WHERE application_id = :applicationId
                ORDER BY id DESC
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public void createPendingPayment(
            String paymentNo,
            Long applicationId,
            Long paymentAccountId,
            BigDecimal amount) {
        String sql = """
                INSERT INTO dbo.payments (
                    payment_no,
                    application_id,
                    payment_account_id,
                    amount,
                    provider,
                    status
                )
                VALUES (
                    :paymentNo,
                    :applicationId,
                    :paymentAccountId,
                    :amount,
                    N'NEWEBPAY',
                    N'PENDING'
                )
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        map.put("applicationId", applicationId);
        map.put("paymentAccountId", paymentAccountId);
        map.put("amount", amount);
        namedParameterJdbcTemplate.update(sql, map);
    }

    public void createPendingPayment(String paymentNo, Long applicationId, BigDecimal amount) {
        createPendingPayment(paymentNo, applicationId, null, amount);
    }

    public Long createRefund(
            String refundNo,
            Long applicationId,
            Long paymentId,
            BigDecimal amount,
            String reason) {
        String sql = """
                INSERT INTO dbo.refunds (
                    refund_no,
                    application_id,
                    payment_id,
                    amount,
                    reason,
                    refund_status
                )
                VALUES (
                    :refundNo,
                    :applicationId,
                    :paymentId,
                    :amount,
                    :reason,
                    N'REFUND_REQUESTED'
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("refundNo", refundNo)
                .addValue("applicationId", applicationId)
                .addValue("paymentId", paymentId)
                .addValue("amount", amount)
                .addValue("reason", reason);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[] { "id" });
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public Optional<Map<String, Object>> findPaymentWithApplication(String paymentNo) {
        String sql = """
                SELECT
                    p.id AS paymentId,
                    p.payment_no AS paymentNo,
                    p.application_id AS applicationId,
                    p.amount,
                    p.provider,
                    p.provider_trade_no AS providerTradeNo,
                    p.payment_account_id AS paymentAccountId,
                    payment_up.user_id AS paymentAccountOrganizerUserId,
                    p.status AS paymentRecordStatus,
                    p.paid_at AS paidAt,
                    a.application_no AS applicationNo,
                    a.user_id AS userId,
                    me.user_id AS organizerUserId,
                    a.payment_status AS applicationPaymentStatus,
                    me.title AS eventTitle,
                    vp.brand_name AS brandName,
                    opa.merchant_id AS merchantId,
                    opa.hash_key_encrypted AS hashKeyEncrypted,
                    opa.hash_iv_encrypted AS hashIvEncrypted,
                    opa.status AS paymentAccountStatus,
                    opa.verification_status AS paymentAccountVerificationStatus
                FROM dbo.payments p
                INNER JOIN dbo.event_applications a ON a.id = p.application_id
                INNER JOIN dbo.market_events me ON me.id = a.event_id
                INNER JOIN dbo.vendor_profiles vp ON vp.id = a.vendor_profile_id
                LEFT JOIN dbo.organizer_payment_accounts opa ON opa.id = p.payment_account_id
                LEFT JOIN dbo.organizer_profiles payment_op
                    ON payment_op.id = opa.organizer_profile_id
                LEFT JOIN dbo.user_profiles payment_up
                    ON payment_up.id = payment_op.user_profile_id
                WHERE p.payment_no = :paymentNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public int markPaymentPaid(String paymentNo, String providerTradeNo, LocalDateTime paidAt) {
        return markPaymentPaid(paymentNo, providerTradeNo, paidAt, null, null);
    }

    public int markPaymentPaid(
            String paymentNo,
            String providerTradeNo,
            LocalDateTime paidAt,
            String responseCode,
            String providerMessage) {
        String sql = """
                UPDATE dbo.payments
                SET
                    status = N'PAID',
                    provider_trade_no = :providerTradeNo,
                    provider_response_code = :responseCode,
                    provider_message = :providerMessage,
                    paid_at = COALESCE(:paidAt, SYSDATETIME()),
                    updated_at = SYSDATETIME()
                WHERE payment_no = :paymentNo
                  AND status <> N'PAID'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        map.put("providerTradeNo", providerTradeNo);
        map.put("paidAt", paidAt);
        map.put("responseCode", responseCode);
        map.put("providerMessage", providerMessage);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int markPaymentFailed(String paymentNo, String providerTradeNo) {
        return markPaymentFailed(paymentNo, providerTradeNo, null, null);
    }

    public int markPaymentFailed(
            String paymentNo,
            String providerTradeNo,
            String responseCode,
            String providerMessage) {
        String sql = """
                UPDATE dbo.payments
                SET
                    status = N'FAILED',
                    provider_trade_no = COALESCE(:providerTradeNo, provider_trade_no),
                    provider_response_code = :responseCode,
                    provider_message = :providerMessage,
                    updated_at = SYSDATETIME()
                WHERE payment_no = :paymentNo
                  AND status = N'PENDING'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        map.put("providerTradeNo", providerTradeNo);
        map.put("responseCode", responseCode);
        map.put("providerMessage", providerMessage);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int updateApplicationPaymentStatus(Long applicationId, String paymentStatus) {
        String sql = """
                UPDATE dbo.event_applications
                SET payment_status = :paymentStatus
                WHERE id = :applicationId
                  AND payment_status <> :paymentStatus
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("applicationId", applicationId);
        map.put("paymentStatus", paymentStatus);
        return namedParameterJdbcTemplate.update(sql, map);
    }
    public Optional<Map<String, Object>> findOrganizerPaymentUserByEmail(String email) {
        String sql = """
                SELECT
                    id AS userId,
                    email,
                    role,
                    status,
                    isLogin,
                    expired_time AS expiredTime
                FROM dbo.users
                WHERE email = :email
                  AND role = 'ORGANIZER'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public Optional<Map<String, Object>> findRefundForOrganizerProcessing(String refundNo) {
        String sql = """
                SELECT TOP 1
                    r.id AS refundId,
                    r.refund_no AS refundNo,
                    r.application_id AS applicationId,
                    r.payment_id AS paymentId,
                    r.amount AS refundAmount,
                    r.reason,
                    r.failed_reason AS failedReason,
                    r.refund_status AS refundStatus,
                    r.refunded_at AS refundedAt,
                    a.application_no AS applicationNo,
                    a.user_id AS vendorUserId,
                    me.user_id AS organizerUserId,
                    me.title AS eventName,
                    p.payment_no AS paymentNo,
                    p.amount AS paymentAmount,
                    p.provider,
                    p.provider_trade_no AS providerTradeNo,
                    p.payment_account_id AS paymentAccountId,
                    payment_up.user_id AS paymentAccountOrganizerUserId,
                    p.status AS paymentStatus,
                    p.paid_at AS paidAt
                FROM dbo.refunds r
                INNER JOIN dbo.event_applications a ON a.id = r.application_id
                INNER JOIN dbo.market_events me ON me.id = a.event_id
                INNER JOIN dbo.payments p ON p.id = r.payment_id
                LEFT JOIN dbo.organizer_payment_accounts opa
                    ON opa.id = p.payment_account_id
                LEFT JOIN dbo.organizer_profiles payment_op
                    ON payment_op.id = opa.organizer_profile_id
                LEFT JOIN dbo.user_profiles payment_up
                    ON payment_up.id = payment_op.user_profile_id
                WHERE r.refund_no = :refundNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("refundNo", refundNo);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public int markRefundProcessing(Long refundId) {
        String sql = """
                UPDATE dbo.refunds
                SET
                    refund_status = N'REFUNDING',
                    failed_reason = NULL
                WHERE id = :refundId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("refundId", refundId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int markRefundSucceeded(Long refundId) {
        String sql = """
                UPDATE dbo.refunds
                SET
                    refund_status = N'REFUNDED',
                    refunded_at = SYSDATETIME(),
                    failed_reason = NULL
                WHERE id = :refundId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("refundId", refundId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int markRefundFailed(Long refundId, String failedReason) {
        String sql = """
                UPDATE dbo.refunds
                SET
                    refund_status = N'REFUND_FAILED',
                    failed_reason = :failedReason
                WHERE id = :refundId
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("refundId", refundId);
        map.put("failedReason", failedReason);
        return namedParameterJdbcTemplate.update(sql, map);
    }
}
