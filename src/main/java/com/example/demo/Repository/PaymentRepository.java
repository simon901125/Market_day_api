package com.example.demo.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    public void createPendingPayment(String paymentNo, Long applicationId, BigDecimal amount) {
        String sql = """
                INSERT INTO dbo.payments (
                    payment_no,
                    application_id,
                    amount,
                    provider,
                    status
                )
                VALUES (
                    :paymentNo,
                    :applicationId,
                    :amount,
                    N'NEWEBPAY',
                    N'PENDING'
                )
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        map.put("applicationId", applicationId);
        map.put("amount", amount);
        namedParameterJdbcTemplate.update(sql, map);
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
                    p.status AS paymentRecordStatus,
                    p.paid_at AS paidAt,
                    a.application_no AS applicationNo,
                    a.user_id AS userId,
                    a.payment_status AS applicationPaymentStatus,
                    me.title AS eventTitle
                FROM dbo.payments p
                INNER JOIN dbo.event_applications a ON a.id = p.application_id
                INNER JOIN dbo.market_events me ON me.id = a.event_id
                WHERE p.payment_no = :paymentNo
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        return RepositoryResultMapper.normalizeOptional(namedParameterJdbcTemplate.queryForList(sql, map).stream().findFirst());
    }

    public int markPaymentPaid(String paymentNo, String providerTradeNo, LocalDateTime paidAt) {
        String sql = """
                UPDATE dbo.payments
                SET
                    status = N'PAID',
                    provider_trade_no = :providerTradeNo,
                    paid_at = COALESCE(:paidAt, SYSDATETIME())
                WHERE payment_no = :paymentNo
                  AND status <> N'PAID'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        map.put("providerTradeNo", providerTradeNo);
        map.put("paidAt", paidAt);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int markPaymentFailed(String paymentNo, String providerTradeNo) {
        String sql = """
                UPDATE dbo.payments
                SET
                    status = N'FAILED',
                    provider_trade_no = COALESCE(:providerTradeNo, provider_trade_no)
                WHERE payment_no = :paymentNo
                  AND status = N'PENDING'
                """;

        Map<String, Object> map = new HashMap<>();
        map.put("paymentNo", paymentNo);
        map.put("providerTradeNo", providerTradeNo);
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
}
