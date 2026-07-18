package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.response.UserResponse;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserRepositoryIT extends SqlServerIntegrationTestSupport {

    private static final String TEST_PASSWORD_HASH = "integration-test-password-hash";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void createsAndFindsLocalVendorWithUserProfile() {
        String email = "user-repository-vendor@example.test";

        assertThat(userRepository.existsByEmail(email)).isFalse();

        Long userId = userRepository.createLocalUser("VENDOR", email, TEST_PASSWORD_HASH);
        userRepository.createUserProfile(userId, "VENDOR", "unused-name", email);

        assertThat(userId).isPositive();
        assertThat(userRepository.existsByEmail(email)).isTrue();

        Map<String, Object> localUser = userRepository.findLocalUserByEmail(email)
                .orElseThrow();
        assertThat(localUser)
                .containsEntry("id", userId)
                .containsEntry("role", "VENDOR")
                .containsEntry("email", email)
                .containsEntry("provider", "LOCAL")
                .containsEntry("status", "UNACTIVE")
                .containsEntry("isLogin", false);

        UserResponse listedUser = userRepository.findAllUsers().stream()
                .filter(user -> email.equals(user.getEmail()))
                .findFirst()
                .orElseThrow();
        assertThat(listedUser.getId()).isEqualTo(userId);
        assertThat(listedUser.getPasswordHash()).isEqualTo(TEST_PASSWORD_HASH);
        assertThat(listedUser.getIsLogin()).isFalse();

        Integer profileCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_profiles
                WHERE user_id = :userId
                  AND profile_type = 'VENDOR'
                """,
                Map.of("userId", userId),
                Integer.class);
        assertThat(profileCount).isEqualTo(1);
    }

    @Test
    void startsAndEndsLocalLoginSession() {
        String email = "user-repository-login@example.test";
        Long userId = userRepository.createLocalUser("VENDOR", email, TEST_PASSWORD_HASH);
        LocalDateTime requestedExpiry = LocalDateTime.now().plusHours(1).withNano(0);

        LocalDateTime actualExpiry = userRepository.startLoginSession(userId, requestedExpiry);

        assertThat(actualExpiry).isEqualTo(requestedExpiry);
        assertThat(userRepository.isCurrentLoginSession(
                email,
                "VENDOR",
                actualExpiry)).isTrue();

        Map<String, Object> loggedInUser = userRepository.findLocalUserByEmail(email)
                .orElseThrow();
        assertThat(loggedInUser)
                .containsEntry("isLogin", true)
                .containsEntry("expiredTime", actualExpiry);

        assertThat(userRepository.markLogoutByEmail(email)).isEqualTo(1);
        assertThat(userRepository.isCurrentLoginSession(
                email,
                "VENDOR",
                actualExpiry)).isFalse();
    }

    @Test
    void verifiesEmailAndManagesEmailAndGenericTokens() {
        String email = "user-repository-token@example.test";
        Long userId = userRepository.createLocalUser("VENDOR", email, TEST_PASSWORD_HASH);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15).withNano(0);

        userRepository.createEmailVerificationToken(userId, "email-code-1", expiresAt);

        Map<String, Object> verification = userRepository
                .findEmailVerificationCode(email, "email-code-1")
                .orElseThrow();
        Long verificationId = ((Number) verification.get("id")).longValue();
        assertThat(verification)
                .containsEntry("user_id", userId)
                .containsEntry("token", "email-code-1")
                .containsEntry("token_type", UserRepository.TOKEN_TYPE_EMAIL_VERIFY)
                .containsEntry("expires_at", expiresAt)
                .containsEntry("email", email)
                .containsEntry("status", "UNACTIVE");

        assertThat(userRepository.findUserToken(
                "email-code-1",
                UserRepository.TOKEN_TYPE_EMAIL_VERIFY)).isPresent();
        assertThat(userRepository.markEmailVerified(userId)).isEqualTo(1);

        Map<String, Object> verifiedUser = userRepository.findLocalUserByEmail(email)
                .orElseThrow();
        assertThat(verifiedUser.get("status")).isEqualTo("ACTIVE");
        assertThat(verifiedUser.get("emailVerifiedAt")).isNotNull();

        assertThat(userRepository.deleteEmailVerificationToken(verificationId)).isEqualTo(1);
        assertThat(userRepository.findEmailVerificationCode(email, "email-code-1")).isEmpty();

        userRepository.createEmailVerificationToken(userId, "email-code-2", expiresAt);
        userRepository.createEmailVerificationToken(userId, "email-code-3", expiresAt);
        assertThat(userRepository.deleteEmailVerificationTokensByUserId(userId)).isEqualTo(2);

        userRepository.createUserToken(
                userId,
                "password-code-1",
                UserRepository.TOKEN_TYPE_PASSWORD_RESET,
                expiresAt);
        Map<String, Object> passwordToken = userRepository.findVerificationCode(
                email,
                "password-code-1",
                UserRepository.TOKEN_TYPE_PASSWORD_RESET).orElseThrow();
        Long passwordTokenId = ((Number) passwordToken.get("id")).longValue();
        assertThat(userRepository.deleteUserToken(
                passwordTokenId,
                UserRepository.TOKEN_TYPE_PASSWORD_RESET)).isEqualTo(1);

        userRepository.createUserToken(
                userId,
                "password-code-2",
                UserRepository.TOKEN_TYPE_PASSWORD_RESET,
                expiresAt);
        userRepository.createUserToken(
                userId,
                "password-code-3",
                UserRepository.TOKEN_TYPE_PASSWORD_RESET,
                expiresAt);
        assertThat(userRepository.deleteUserTokensByUserId(
                userId,
                UserRepository.TOKEN_TYPE_PASSWORD_RESET)).isEqualTo(2);
    }

    @Test
    void createsGoogleAndSystemAdminUsersAndBindsGoogleToLocalAccount() {
        String googleEmail = "user-repository-google@example.test";
        Long googleUserId = userRepository.createGoogleUser(
                "VENDOR",
                googleEmail,
                "google-sub-direct");

        assertThat(googleUserId).isPositive();
        assertThat(userRepository.findGoogleUserBySub("google-sub-direct"))
                .hasValueSatisfying(user -> assertThat(user)
                        .containsEntry("id", googleUserId)
                        .containsEntry("email", googleEmail)
                        .containsEntry("provider", "GOOGLE")
                        .containsEntry("googleSub", "google-sub-direct"));
        assertThat(userRepository.findProfileByEmail(googleEmail)).isPresent();
        assertThat(userRepository.findLocalUserByEmail(googleEmail)).isEmpty();

        String localEmail = "user-repository-bind@example.test";
        Long localUserId = userRepository.createLocalUser(
                "VENDOR",
                localEmail,
                TEST_PASSWORD_HASH);
        assertThat(userRepository.bindGoogleAccountByEmail(
                localEmail,
                "google-sub-bound")).isEqualTo(1);
        assertThat(userRepository.bindGoogleAccountByEmail(
                localEmail,
                "google-sub-bound-again")).isZero();

        assertThat(userRepository.findGoogleUserBySub("google-sub-bound"))
                .hasValueSatisfying(user -> assertThat(user)
                        .containsEntry("id", localUserId)
                        .containsEntry("provider", "BOTH")
                        .containsEntry("status", "ACTIVE"));
        assertThat(userRepository.findLocalUserByEmail(localEmail)).isPresent();

        String adminEmail = "user-repository-admin@example.test";
        Long adminId = userRepository.createSystemAdmin(adminEmail, TEST_PASSWORD_HASH);
        Map<String, Object> admin = userRepository.findLocalUserByEmail(adminEmail)
                .orElseThrow();
        assertThat(admin)
                .containsEntry("id", adminId)
                .containsEntry("role", "ADMIN")
                .containsEntry("provider", "LOCAL")
                .containsEntry("status", "ACTIVE")
                .containsEntry("isLogin", false);
        assertThat(admin.get("emailVerifiedAt")).isNotNull();
    }

    @Test
    void updatesLocalPasswordAndDeactivatesActiveUser() {
        String email = "user-repository-password@example.test";
        Long userId = userRepository.createLocalUser("VENDOR", email, TEST_PASSWORD_HASH);

        assertThat(userRepository.updateLocalPasswordByEmail(email, "hash-by-email"))
                .isEqualTo(1);
        assertThat(passwordHash(userId)).isEqualTo("hash-by-email");

        assertThat(userRepository.updateLocalPasswordByUserId(userId, "hash-by-id"))
                .isEqualTo(1);
        assertThat(passwordHash(userId)).isEqualTo("hash-by-id");

        assertThat(userRepository.markEmailVerified(userId)).isEqualTo(1);
        LocalDateTime expiry = userRepository.startLoginSession(
                userId,
                LocalDateTime.now().plusHours(1).withNano(0));
        assertThat(userRepository.isCurrentLoginSession(email, "VENDOR", expiry)).isTrue();

        assertThat(userRepository.deactivateUserById(userId)).isEqualTo(1);
        Map<String, Object> disabledUser = userRepository.findLocalUserByEmail(email)
                .orElseThrow();
        assertThat(disabledUser)
                .containsEntry("status", "DISABLED")
                .containsEntry("isLogin", false);
        assertThat(userRepository.deactivateUserById(userId)).isZero();

        Long googleUserId = userRepository.createGoogleUser(
                "VENDOR",
                "user-repository-google-password@example.test",
                "google-sub-password");
        assertThat(userRepository.updateLocalPasswordByUserId(googleUserId, "not-allowed"))
                .isZero();
    }

    @Test
    void automaticallyLogsOutExpiredUsersAndReturnsAffectedIds() {
        Long firstUserId = userRepository.createLocalUser(
                "VENDOR",
                "user-repository-expired-1@example.test",
                TEST_PASSWORD_HASH);
        setExpiredLogin(firstUserId);

        List<Long> loggedOutIds = userRepository.autoLogoutExpiredUsersAndReturnIds();
        assertThat(loggedOutIds).contains(firstUserId);
        assertThat(isLoggedIn(firstUserId)).isFalse();

        Long secondUserId = userRepository.createLocalUser(
                "VENDOR",
                "user-repository-expired-2@example.test",
                TEST_PASSWORD_HASH);
        setExpiredLogin(secondUserId);

        assertThat(userRepository.autoLogoutExpiredUsers()).isEqualTo(1);
        assertThat(isLoggedIn(secondUserId)).isFalse();
    }

    @Test
    void detectsActiveVendorApplicationsAndOrganizerEvents() {
        Long categoryId = firstCategoryId();
        Long organizerId = userRepository.createLocalUser(
                "ORGANIZER",
                "user-repository-organizer@example.test",
                TEST_PASSWORD_HASH);
        Long eventId = createFutureEvent(organizerId, categoryId);

        assertThat(userRepository.existsActiveOrganizerEvent(organizerId)).isTrue();
        jdbcTemplate.update(
                "UPDATE market_events SET workflow_status = 'CANCELLED' WHERE id = :eventId",
                Map.of("eventId", eventId));
        assertThat(userRepository.existsActiveOrganizerEvent(organizerId)).isFalse();

        Long vendorId = userRepository.createLocalUser(
                "VENDOR",
                "user-repository-applicant@example.test",
                TEST_PASSWORD_HASH);
        userRepository.createUserProfile(vendorId, "VENDOR", "unused-name", "unused@example.test");
        Long vendorProfileId = createVendorProfile(vendorId, categoryId);

        jdbcTemplate.update(
                """
                INSERT INTO event_applications (
                    application_no, event_id, user_id, vendor_profile_id, total_amount
                ) VALUES (
                    'USER-REPOSITORY-APP-1', :eventId, :userId, :vendorProfileId, 1000
                )
                """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("userId", vendorId)
                        .addValue("vendorProfileId", vendorProfileId));

        assertThat(userRepository.existsActiveVendorApplication(vendorId)).isTrue();
        jdbcTemplate.update(
                "UPDATE event_applications SET is_cancelled = 1 WHERE user_id = :userId",
                Map.of("userId", vendorId));
        assertThat(userRepository.existsActiveVendorApplication(vendorId)).isFalse();
    }

    private String passwordHash(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = :userId",
                Map.of("userId", userId),
                String.class);
    }

    private void setExpiredLogin(Long userId) {
        jdbcTemplate.update(
                """
                UPDATE users
                SET isLogin = 1,
                    expired_time = DATEADD(MINUTE, -5, SYSDATETIME())
                WHERE id = :userId
                """,
                Map.of("userId", userId));
    }

    private boolean isLoggedIn(Long userId) {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT isLogin FROM users WHERE id = :userId",
                Map.of("userId", userId),
                Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private Long firstCategoryId() {
        return jdbcTemplate.queryForObject(
                "SELECT TOP 1 id FROM categories ORDER BY id",
                Map.of(),
                Long.class);
    }

    private Long createFutureEvent(Long organizerId, Long categoryId) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long eventId = jdbcTemplate.queryForObject(
                """
                INSERT INTO market_events (
                    user_id, title, summary, description,
                    location_name, city, address,
                    start_at, end_at, registration_start_at, registration_end_at,
                    max_booths, base_fee
                )
                OUTPUT INSERTED.id
                VALUES (
                    :userId, N'整合測試活動', N'整合測試摘要', N'整合測試介紹',
                    N'整合測試場地', N'台北市', N'整合測試地址',
                    :startAt, :endAt, :registrationStartAt, :registrationEndAt,
                    10, 1000
                )
                """,
                new MapSqlParameterSource()
                        .addValue("userId", organizerId)
                        .addValue("startAt", now.plusDays(1))
                        .addValue("endAt", now.plusDays(2))
                        .addValue("registrationStartAt", now.minusDays(1))
                        .addValue("registrationEndAt", now.plusHours(1)),
                Long.class);
        jdbcTemplate.update("""
                INSERT INTO market_event_categories (event_id, category_id)
                VALUES (:eventId, :categoryId)
                """, Map.of("eventId", eventId, "categoryId", categoryId));
        return eventId;
    }

    private Long createVendorProfile(Long userId, Long categoryId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("categoryId", categoryId)
                .addValue("userId", userId);

        jdbcTemplate.update(
                """
                INSERT INTO vendor_profiles (user_profile_id, category_id, brand_name)
                SELECT id, :categoryId, N'整合測試品牌'
                FROM user_profiles
                WHERE user_id = :userId
                  AND profile_type = 'VENDOR'
                """,
                parameters);

        Long vendorProfileId = jdbcTemplate.queryForObject(
                """
                SELECT vp.id
                FROM vendor_profiles vp
                INNER JOIN user_profiles up ON up.id = vp.user_profile_id
                WHERE up.user_id = :userId
                  AND up.profile_type = 'VENDOR'
                """,
                parameters,
                Long.class);
        return vendorProfileId;
    }
}
