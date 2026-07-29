package com.example.demo.Repository;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizerPaymentAccountRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OrganizerPaymentAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Map<String, Object>> findOrganizerByEmail(String email) {
        return one("""
                SELECT u.id AS userId, u.role, u.status AS userStatus,
                       op.id AS organizerProfileId
                FROM dbo.users u
                INNER JOIN dbo.user_profiles up
                    ON up.user_id = u.id AND up.profile_type = N'ORGANIZER'
                INNER JOIN dbo.organizer_profiles op ON op.user_profile_id = up.id
                WHERE u.email = :email
                """, Map.of("email", email));
    }

    public Optional<Map<String, Object>> findByOrganizerProfileId(Long organizerProfileId) {
        return one(accountSelect() + """
                WHERE opa.organizer_profile_id = :organizerProfileId
                """, Map.of("organizerProfileId", organizerProfileId));
    }

    public Optional<Map<String, Object>> findActiveByOrganizerUserId(Long organizerUserId) {
        return one(accountSelect() + """
                INNER JOIN dbo.organizer_profiles op ON op.id = opa.organizer_profile_id
                INNER JOIN dbo.user_profiles up ON up.id = op.user_profile_id
                WHERE up.user_id = :organizerUserId
                  AND opa.status = 'ACTIVE'
                  AND opa.verification_status = 'VERIFIED'
                """, Map.of("organizerUserId", organizerUserId));
    }

    public Optional<Map<String, Object>> findByOrganizerUserId(Long organizerUserId) {
        return one(accountSelect() + """
                INNER JOIN dbo.organizer_profiles op ON op.id = opa.organizer_profile_id
                INNER JOIN dbo.user_profiles up ON up.id = op.user_profile_id
                WHERE up.user_id = :organizerUserId
                """, Map.of("organizerUserId", organizerUserId));
    }

    public Optional<Map<String, Object>> findActiveByMerchantId(String merchantId) {
        return one(accountSelect() + """
                WHERE opa.merchant_id = :merchantId
                  AND opa.status = 'ACTIVE'
                  AND opa.verification_status = 'VERIFIED'
                """, Map.of("merchantId", merchantId));
    }

    public Optional<Map<String, Object>> findByMerchantId(String merchantId) {
        return one(accountSelect() + """
                WHERE opa.merchant_id = :merchantId
                """, Map.of("merchantId", merchantId));
    }

    public boolean hasPayments(Long paymentAccountId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT_BIG(1)
                FROM dbo.payments
                WHERE payment_account_id = :paymentAccountId
                """, Map.of("paymentAccountId", paymentAccountId), Long.class);
        return count != null && count > 0;
    }

    public long upsert(
            Long organizerProfileId,
            String merchantId,
            String encryptedHashKey,
            String encryptedHashIv) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizerProfileId", organizerProfileId)
                .addValue("merchantId", merchantId)
                .addValue("hashKey", encryptedHashKey)
                .addValue("hashIv", encryptedHashIv);
        int updated = jdbc.update("""
                UPDATE dbo.organizer_payment_accounts
                SET merchant_id = :merchantId,
                    hash_key_encrypted = :hashKey,
                    hash_iv_encrypted = :hashIv,
                    status = 'DISABLED',
                    verification_status = 'UNVERIFIED',
                    verification_no = NULL,
                    verified_at = NULL,
                    updated_at = SYSDATETIME()
                WHERE organizer_profile_id = :organizerProfileId
                """, parameters);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO dbo.organizer_payment_accounts (
                        organizer_profile_id, merchant_id,
                        hash_key_encrypted, hash_iv_encrypted, status
                    ) VALUES (
                        :organizerProfileId, :merchantId,
                        :hashKey, :hashIv, 'DISABLED'
                    )
                    """, parameters);
        }
        return findByOrganizerProfileId(organizerProfileId)
                .map(row -> ((Number) row.get("paymentAccountId")).longValue())
                .orElseThrow(() -> new IllegalStateException("Payment account was not saved"));
    }

    public int bindUnboundEvents(Long organizerUserId, Long paymentAccountId) {
        return jdbc.update("""
                UPDATE dbo.market_events
                SET payment_account_id = :paymentAccountId
                WHERE user_id = :organizerUserId
                  AND payment_account_id IS NULL
                """, Map.of(
                "organizerUserId", organizerUserId,
                "paymentAccountId", paymentAccountId));
    }

    public void createVerification(
            Long paymentAccountId,
            String verificationNo) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("paymentAccountId", paymentAccountId)
                .addValue("verificationNo", verificationNo);
        int updated = jdbc.update("""
                UPDATE dbo.organizer_payment_accounts
                SET status = 'DISABLED',
                    verification_status = 'PENDING',
                    verification_no = :verificationNo,
                    verified_at = NULL,
                    updated_at = SYSDATETIME()
                WHERE id = :paymentAccountId
                """, parameters);
        if (updated != 1) {
            throw new IllegalStateException(
                    "建立藍新驗證交易失敗：找不到指定的主辦方金流帳戶");
        }
    }

    public Optional<Map<String, Object>> findVerificationByNo(String verificationNo) {
        return one("""
                SELECT opa.id AS paymentAccountId,
                       opa.verification_no AS verificationNo,
                       CAST(1 AS DECIMAL(10,2)) AS amount,
                       opa.merchant_id AS merchantId,
                       opa.hash_key_encrypted AS hashKeyEncrypted,
                       opa.hash_iv_encrypted AS hashIvEncrypted,
                       opa.status AS paymentAccountStatus,
                       opa.verification_status AS paymentAccountVerificationStatus
                FROM dbo.organizer_payment_accounts opa
                WHERE opa.verification_no = :verificationNo
                """, Map.of("verificationNo", verificationNo));
    }

    public void completeVerification(String verificationNo) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("verificationNo", verificationNo);
        jdbc.update("""
                UPDATE dbo.organizer_payment_accounts
                SET status = 'ACTIVE',
                    verification_status = 'VERIFIED',
                    verified_at = SYSDATETIME(),
                    updated_at = SYSDATETIME()
                WHERE verification_no = :verificationNo
                """, parameters);
    }

    public void failVerification(String verificationNo) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("verificationNo", verificationNo);
        jdbc.update("""
                UPDATE dbo.organizer_payment_accounts
                SET status = 'DISABLED',
                    verification_status = 'FAILED',
                    verified_at = NULL,
                    updated_at = SYSDATETIME()
                WHERE verification_no = :verificationNo
                  AND verification_status = 'PENDING'
                """, parameters);
    }

    private String accountSelect() {
        return """
                SELECT opa.id AS paymentAccountId,
                       opa.organizer_profile_id AS organizerProfileId,
                       opa.merchant_id AS merchantId,
                       opa.hash_key_encrypted AS hashKeyEncrypted,
                       opa.hash_iv_encrypted AS hashIvEncrypted,
                       opa.status,
                       opa.verification_status AS verificationStatus,
                       opa.verification_no AS verificationNo,
                       opa.verified_at AS verifiedAt,
                       opa.created_at AS createdAt,
                       opa.updated_at AS updatedAt
                FROM dbo.organizer_payment_accounts opa
                """;
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> parameters) {
        return RepositoryResultMapper.normalizeOptional(jdbc.queryForList(sql, parameters).stream().findFirst());
    }
}
