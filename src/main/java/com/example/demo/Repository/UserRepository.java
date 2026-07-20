package com.example.demo.Repository;

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

import com.example.demo.dto.response.UserResponse;

@Repository
public class UserRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public static final String TOKEN_TYPE_EMAIL_VERIFY = "EMAIL_VERIFY";
    public static final String TOKEN_TYPE_PASSWORD_RESET = "PASSWORD_RESET";

    public List<UserResponse> findAllUsers() {
        String sql = """
                SELECT id, role, email, password_hash, provider, google_sub, status, isLogin, email_verified_at, expired_time, created_at, updated_at
                FROM users
                """;

        return namedParameterJdbcTemplate.query(sql, (rs, rowNum) -> {
            UserResponse user = new UserResponse();
            user.setId(rs.getLong("id"));
            user.setRole(rs.getString("role"));
            user.setEmail(rs.getString("email"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setProvider(rs.getString("provider"));
            user.setGoogleSub(rs.getString("google_sub"));
            user.setStatus(rs.getString("status"));
            user.setIsLogin(rs.getBoolean("isLogin"));
            user.setEmailVerifiedAt(rs.getObject("email_verified_at", LocalDateTime.class));
            user.setExpiredTime(rs.getObject("expired_time", LocalDateTime.class));
            user.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            user.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
            return user;
        });
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = :email";
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, map, Integer.class);
        return count != null && count > 0;
    }

    public int countRecentLocalLoginFailures(Long userId, LocalDateTime since) {
        Integer count = namedParameterJdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.request_logs
                WHERE user_id = :userId
                  AND method = N'POST'
                  AND path IN (
                      N'/api/vendor/local-login',
                      N'/api/organizer/local-login',
                      N'/api/admin/local-login'
                  )
                  AND (status_code IS NULL OR status_code < 200 OR status_code >= 300)
                  AND created_at >= :since
                """, new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("since", since), Integer.class);
        return count == null ? 0 : count;
    }

    public Long createLocalUser(String role, String email, String passwordHash) {
        String sql = """
                INSERT INTO users (role, email, password_hash, provider)
                VALUES (:role, :email, :passwordHash, :provider)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("role", role)
                .addValue("email", email)
                .addValue("passwordHash", passwordHash)
                .addValue("provider", "LOCAL");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[] {"id"});
        return keyHolder.getKey().longValue();
    }

    public Long createSystemAdmin(String email, String passwordHash) {
        String sql = """
                INSERT INTO users (role, email, password_hash, provider, status, isLogin, email_verified_at)
                VALUES (:role, :email, :passwordHash, :provider, :status, :isLogin, SYSDATETIME())
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("role", "ADMIN")
                .addValue("email", email)
                .addValue("passwordHash", passwordHash)
                .addValue("provider", "LOCAL")
                .addValue("status", "ACTIVE")
                .addValue("isLogin", false);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[] {"id"});
        return keyHolder.getKey().longValue();
    }

    public Long createGoogleUser(String role, String email, String googleSub) {
        String sql = """
                INSERT INTO users (role, email, password_hash, provider, google_sub)
                VALUES (:role, :email, :passwordHash, :provider, :googleSub)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("role", role)
                .addValue("email", email)
                .addValue("passwordHash", null)
                .addValue("provider", "GOOGLE")
                .addValue("googleSub", googleSub);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedParameterJdbcTemplate.update(sql, params, keyHolder, new String[] {"id"});
        return keyHolder.getKey().longValue();
    }

    public void createUserProfile(Long userId, String profileType, String name, String email) {
        String sql = """
                INSERT INTO user_profiles (
                    user_id, profile_type, contact_name, contact_email
                )
                VALUES (
                    :userId, :profileType, :contactName, :contactEmail
                )
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("profileType", profileType);
        map.put("contactName", name);
        map.put("contactEmail", email);
        namedParameterJdbcTemplate.update(sql, map);
    }

    public Optional<Map<String, Object>> findLocalUserByEmail(String email) {
        String sql = """
                SELECT u.id, u.role, u.email, u.password_hash, u.provider, u.status, u.isLogin,
                       COALESCE(vp.brand_name, op.organizer_name, up.contact_name) AS name,
                       up.contact_phone AS phone,
                       u.google_sub AS googleSub,
                       u.email_verified_at AS emailVerifiedAt,
                       u.expired_time AS expiredTime,
                       u.created_at AS createdAt,
                       u.updated_at AS updatedAt
                FROM users u
                    LEFT JOIN user_profiles up ON up.user_id = u.id AND up.profile_type = u.role
                    LEFT JOIN vendor_profiles vp ON vp.user_profile_id = up.id
                    LEFT JOIN organizer_profiles op ON op.user_profile_id = up.id
                WHERE u.email = :email
                  AND u.provider IN ('LOCAL', 'BOTH')
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, map);
        return RepositoryResultMapper.normalizeOptional(list.stream().findFirst());
    }

    public Optional<Map<String, Object>> findUserAccountById(Long userId) {
        String sql = """
                SELECT id, role, email, status
                FROM dbo.users
                WHERE id = :userId
                """;
        return RepositoryResultMapper.normalizeOptional(
                namedParameterJdbcTemplate.queryForList(sql, Map.of("userId", userId)).stream().findFirst());
    }

    public Optional<Map<String, Object>> findProfileByEmail(String email) {
        String sql = """
                SELECT u.id, u.role, u.email, u.provider,
                       COALESCE(vp.brand_name, op.organizer_name, up.contact_name) AS name,
                       up.contact_phone AS phone,
                       u.google_sub AS googleSub,
                       u.status, u.isLogin,
                       u.email_verified_at AS emailVerifiedAt,
                       u.expired_time AS expiredTime,
                       u.created_at AS createdAt,
                       u.updated_at AS updatedAt
                FROM users u
                    LEFT JOIN user_profiles up ON up.user_id = u.id AND up.profile_type = u.role
                    LEFT JOIN vendor_profiles vp ON vp.user_profile_id = up.id
                    LEFT JOIN organizer_profiles op ON op.user_profile_id = up.id
                WHERE u.email = :email
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, map);
        return RepositoryResultMapper.normalizeOptional(list.stream().findFirst());
    }

    public Optional<Map<String, Object>> findGoogleUserBySub(String googleSub) {
        String sql = """
                SELECT u.id, u.role, u.email, u.provider,
                       COALESCE(vp.brand_name, op.organizer_name, up.contact_name) AS name,
                       up.contact_phone AS phone,
                       u.google_sub AS googleSub,
                       u.status, u.isLogin,
                       u.email_verified_at AS emailVerifiedAt,
                       u.expired_time AS expiredTime,
                       u.created_at AS createdAt,
                       u.updated_at AS updatedAt
                FROM users u
                    LEFT JOIN user_profiles up ON up.user_id = u.id AND up.profile_type = u.role
                    LEFT JOIN vendor_profiles vp ON vp.user_profile_id = up.id
                    LEFT JOIN organizer_profiles op ON op.user_profile_id = up.id
                WHERE u.google_sub = :googleSub
                  AND u.provider IN ('GOOGLE', 'BOTH')
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("googleSub", googleSub);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, map);
        return RepositoryResultMapper.normalizeOptional(list.stream().findFirst());
    }

    public int bindGoogleAccountByEmail(String email, String googleSub) {
        String sql = """
                UPDATE users
                SET provider = 'BOTH',
                    google_sub = :googleSub,
                    email_verified_at = COALESCE(email_verified_at, SYSDATETIME()),
                    status = CASE WHEN status = 'UNACTIVE' THEN 'ACTIVE' ELSE status END,
                    updated_at = SYSDATETIME()
                WHERE email = :email
                  AND provider = 'LOCAL'
                  AND google_sub IS NULL
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        map.put("googleSub", googleSub);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public boolean existsActiveVendorApplication(Long userId) {
        String sql = """
                SELECT COUNT(*)
                FROM event_applications
                WHERE user_id = :userId
                  AND is_cancelled = 0
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, map, Integer.class);
        return count != null && count > 0;
    }

    public boolean existsActiveOrganizerEvent(Long userId) {
        String sql = """
                SELECT COUNT(*)
                FROM market_events
                WHERE user_id = :userId
                  AND end_at >= SYSDATETIME()
                  AND workflow_status NOT IN (N'UNPUBLISHED', N'CANCELLED')
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, map, Integer.class);
        return count != null && count > 0;
    }

    public int deactivateUserById(Long userId) {
        String sql = """
                UPDATE users
                SET status = 'DISABLED',
                    isLogin = 0,
                    updated_at = SYSDATETIME()
                WHERE id = :userId
                  AND status = 'ACTIVE'
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public LocalDateTime startLoginSession(Long userId, LocalDateTime requestedExpiry) {
        String sql = """
                DECLARE @updated TABLE (expired_time DATETIME2);

                UPDATE users
                SET isLogin = 1,
                    expired_time = CASE
                        WHEN expired_time >= :requestedExpiry
                            THEN DATEADD(SECOND, 1, expired_time)
                        ELSE :requestedExpiry
                    END
                OUTPUT inserted.expired_time INTO @updated
                WHERE id = :userId

                SELECT TOP 1 expired_time
                FROM @updated
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("requestedExpiry", requestedExpiry.withNano(0));
        return namedParameterJdbcTemplate.queryForObject(sql, map, LocalDateTime.class);
    }

    public int markLogoutByEmail(String email) {
        String sql = """
                UPDATE users
                SET isLogin = 0
                WHERE email = :email
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int autoLogoutExpiredUsers() {
        String sql = """
                UPDATE users
                SET isLogin = 0
                WHERE isLogin = 1
                  AND expired_time <= SYSDATETIME()
        """;
        return namedParameterJdbcTemplate.update(sql, Map.of());
    }

    public List<Long> autoLogoutExpiredUsersAndReturnIds() {
        String sql = """
                DECLARE @updated_users TABLE (id BIGINT);

                UPDATE users
                SET isLogin = 0
                OUTPUT inserted.id INTO @updated_users
                WHERE isLogin = 1
                  AND expired_time <= SYSDATETIME()

                SELECT id
                FROM @updated_users
                """;
        return namedParameterJdbcTemplate.queryForList(sql, Map.of(), Long.class);
    }

    public boolean isCurrentLoginSession(
            String email,
            String role,
            LocalDateTime expiresAt) {
        String sql = """
                SELECT COUNT(*)
                FROM users
                WHERE email = :email
                  AND role = :role
                  AND isLogin = 1
                  AND expired_time = :expiresAt
                  AND expired_time > SYSDATETIME()
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        map.put("role", role);
        map.put("expiresAt", expiresAt.withNano(0));
        Integer count = namedParameterJdbcTemplate.queryForObject(sql, map, Integer.class);
        return count != null && count > 0;
    }

    public int updateLocalPasswordByEmail(String email, String passwordHash) {
        String sql = """
                UPDATE users
                SET password_hash = :passwordHash,
                    updated_at = SYSDATETIME()
                WHERE email = :email
                  AND provider IN ('LOCAL', 'BOTH')
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        map.put("passwordHash", passwordHash);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int updateLocalPasswordByUserId(Long userId, String passwordHash) {
        String sql = """
                UPDATE users
                SET password_hash = :passwordHash,
                    updated_at = SYSDATETIME()
                WHERE id = :userId
                  AND provider IN ('LOCAL', 'BOTH')
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("passwordHash", passwordHash);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public void createEmailVerificationToken(Long userId, String token, LocalDateTime expiresAt) {
        createUserToken(userId, token, TOKEN_TYPE_EMAIL_VERIFY, expiresAt);
    }

    public void createUserToken(Long userId, String token, String tokenType, LocalDateTime expiresAt) {
        String sql = """
                INSERT INTO user_tokens (user_id, token, token_type, expires_at)
                VALUES (:userId, :token, :tokenType, :expiresAt)
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("token", token);
        map.put("tokenType", tokenType);
        map.put("expiresAt", expiresAt);
        namedParameterJdbcTemplate.update(sql, map);
    }

    public int deleteEmailVerificationTokensByUserId(Long userId) {
        return deleteUserTokensByUserId(userId, TOKEN_TYPE_EMAIL_VERIFY);
    }

    public int deleteUserTokensByUserId(Long userId, String tokenType) {
        String sql = """
                DELETE FROM user_tokens
                WHERE user_id = :userId
                  AND token_type = :tokenType
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("tokenType", tokenType);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public Optional<Map<String, Object>> findEmailVerificationCode(String email, String code) {
        return findVerificationCode(email, code, TOKEN_TYPE_EMAIL_VERIFY);
    }

    public Optional<Map<String, Object>> findVerificationCode(String email, String code, String tokenType) {
        String sql = """
                SELECT user_tokens.id, user_tokens.user_id, user_tokens.token, user_tokens.token_type,
                       user_tokens.expires_at, users.email, users.status, users.email_verified_at
                FROM user_tokens
                    INNER JOIN users ON users.id = user_tokens.user_id
                WHERE users.email = :email
                  AND user_tokens.token = :code
                  AND user_tokens.token_type = :tokenType
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        map.put("code", code);
        map.put("tokenType", tokenType);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, map);
        return RepositoryResultMapper.normalizeOptional(list.stream().findFirst());
    }

    public Optional<Map<String, Object>> findUserToken(String token, String tokenType) {
        String sql = """
                SELECT user_tokens.id, user_tokens.user_id, user_tokens.token, user_tokens.token_type,
                       user_tokens.expires_at, users.email, users.provider, users.status
                FROM user_tokens
                    INNER JOIN users ON users.id = user_tokens.user_id
                WHERE user_tokens.token = :token
                  AND user_tokens.token_type = :tokenType
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("tokenType", tokenType);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, map);
        return RepositoryResultMapper.normalizeOptional(list.stream().findFirst());
    }

    public int markEmailVerified(Long userId) {
        String sql = """
                UPDATE users
                SET email_verified_at = COALESCE(email_verified_at, SYSDATETIME()),
                    status = 'ACTIVE'
                WHERE id = :userId
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        return namedParameterJdbcTemplate.update(sql, map);
    }

    public int deleteEmailVerificationToken(Long tokenId) {
        return deleteUserToken(tokenId, TOKEN_TYPE_EMAIL_VERIFY);
    }

    public int deleteUserToken(Long tokenId, String tokenType) {
        String sql = """
                DELETE FROM user_tokens
                WHERE id = :tokenId
                  AND token_type = :tokenType
                """;
        Map<String, Object> map = new HashMap<>();
        map.put("tokenId", tokenId);
        map.put("tokenType", tokenType);
        return namedParameterJdbcTemplate.update(sql, map);
    }
}
