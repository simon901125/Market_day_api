package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.StatusLogRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.log.StatusLogEntry;
import com.example.demo.dto.request.EmailVerificationRequest;
import com.example.demo.dto.request.GoogleCredentialRequest;
import com.example.demo.dto.request.LocalLoginRequest;
import com.example.demo.dto.request.LocalRegisterRequest;
import com.example.demo.dto.request.RequestPasswordResetRequest;
import com.example.demo.dto.request.ResetPasswordRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.GoogleTokenInfo;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.PasswordResetVerificationResponse;
import com.example.demo.dto.response.UserProfileResponse;
import com.example.demo.dto.response.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String AUTHORIZATION = "Bearer login-token";
    private static final String TOKEN = "login-token";
    private static final String EMAIL = "vendor1@example.test";
    private static final String PASSWORD = "a12345678";
    private static final String GOOGLE_CLIENT_ID = "test-google-client-id";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RequestLogRepository requestLogRepository;
    @Mock
    private StatusLogRepository statusLogRepository;
    @Mock
    private AuthService authService;
    @Mock
    private JwtService jwtService;
    @Mock
    private UpdateActiveTimeService updateActiveTimeService;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService();
        ReflectionTestUtils.setField(userService, "userRepository", userRepository);
        ReflectionTestUtils.setField(userService, "requestLogRepository", requestLogRepository);
        ReflectionTestUtils.setField(userService, "statusLogRepository", statusLogRepository);
        ReflectionTestUtils.setField(userService, "authService", authService);
        ReflectionTestUtils.setField(userService, "jwtService", jwtService);
        ReflectionTestUtils.setField(userService, "updateActiveTimeService", updateActiveTimeService);
        ReflectionTestUtils.setField(userService, "emailService", emailService);
        ReflectionTestUtils.setField(userService, "notificationService", notificationService);
        ReflectionTestUtils.setField(userService, "googleClientId", GOOGLE_CLIENT_ID);
    }

    @Test
    void findsAllUsers() {
        UserResponse user = new UserResponse();
        user.setId(1L);
        when(userRepository.findAllUsers()).thenReturn(List.of(user));

        ApiResponse<List<UserResponse>> response = userService.findAllUsers();

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData()).containsExactly(user);
    }

    @Test
    void registersLocalUserAndSendsVerificationCode() {
        LocalRegisterRequest request = localRegisterRequest();
        when(authService.hashPassword(PASSWORD)).thenReturn("hashed-password");
        when(userRepository.createLocalUser("VENDOR", EMAIL, "hashed-password")).thenReturn(10L);

        ApiResponse<Void> response = userService.registerLocal(request, "VENDOR");

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).createUserProfile(10L, "VENDOR", "測試攤主", EMAIL);
        verify(userRepository).deleteEmailVerificationTokensByUserId(10L);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(userRepository).createEmailVerificationToken(eq(10L), code.capture(), any(LocalDateTime.class));
        assertThat(code.getValue()).matches("\\d{6}");
        verify(emailService).sendVerificationCode(EMAIL, code.getValue());
    }

    @Test
    void rejectsDuplicateOrAdminLocalRegistration() {
        LocalRegisterRequest request = localRegisterRequest();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThat(userService.registerLocal(request, "VENDOR").isSuccessStatus()).isFalse();
        assertThat(userService.registerLocal(request, "ADMIN").isSuccessStatus()).isFalse();
        verify(userRepository, never()).createLocalUser(anyString(), anyString(), anyString());
    }

    @Test
    void registersGoogleUserAfterCredentialValidation() {
        GoogleCredentialRequest request = googleRequest();
        GoogleTokenInfo tokenInfo = googleTokenInfo();
        when(authService.verifyGoogleCredential("google-credential")).thenReturn(tokenInfo);
        when(userRepository.createGoogleUser("ORGANIZER", EMAIL, "google-sub")).thenReturn(20L);

        ApiResponse<Void> response = userService.registerGoogle(request, "ORGANIZER");

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).createUserProfile(20L, "ORGANIZER", "Google User", EMAIL);
        verify(emailService).sendVerificationCode(eq(EMAIL), anyString());
        verify(notificationService).notifyAdminsOrganizerRegistrationSubmitted(20L, "Google User", EMAIL);
    }

    @Test
    void rejectsGoogleRegistrationForWrongAudience() {
        GoogleTokenInfo tokenInfo = googleTokenInfo();
        tokenInfo.setAud("another-client");
        when(authService.verifyGoogleCredential("google-credential")).thenReturn(tokenInfo);

        assertThat(userService.registerGoogle(googleRequest(), "VENDOR").isSuccessStatus()).isFalse();
        verify(userRepository, never()).createGoogleUser(anyString(), anyString(), anyString());
    }

    @Test
    void logsInLocalUserAndCreatesJwtForDatabaseSessionExpiry() {
        LocalDateTime expiry = LocalDateTime.now().plusHours(1).withNano(0);
        when(userRepository.findLocalUserByEmail(EMAIL)).thenReturn(Optional.of(activeUser("VENDOR")));
        when(authService.matchesPassword(PASSWORD, "stored-hash")).thenReturn(true);
        when(jwtService.calculateExpiration()).thenReturn(expiry);
        when(userRepository.startLoginSession(10L, expiry)).thenReturn(expiry);
        when(jwtService.generateToken(EMAIL, "VENDOR", expiry)).thenReturn("jwt-value");

        ApiResponse<LoginResponse> response = userService.loginLocal(localLoginRequest(), "VENDOR");

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getToken()).isEqualTo("jwt-value");
        assertThat(response.getData().getUser().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void rejectsLocalLoginForBadPasswordOrWrongPortal() {
        when(userRepository.findLocalUserByEmail(EMAIL)).thenReturn(Optional.of(activeUser("VENDOR")));
        when(authService.matchesPassword(PASSWORD, "stored-hash")).thenReturn(false, true);

        assertThat(userService.loginLocal(localLoginRequest(), "VENDOR").isSuccessStatus()).isFalse();
        assertThat(userService.loginLocal(localLoginRequest(), "ORGANIZER").isSuccessStatus()).isFalse();
        verify(userRepository, never()).startLoginSession(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void logsInGoogleUser() {
        LocalDateTime expiry = LocalDateTime.now().plusHours(1).withNano(0);
        when(authService.verifyGoogleCredential("google-credential")).thenReturn(googleTokenInfo());
        when(userRepository.findGoogleUserBySub("google-sub")).thenReturn(Optional.of(activeUser("VENDOR")));
        when(jwtService.calculateExpiration()).thenReturn(expiry);
        when(userRepository.startLoginSession(10L, expiry)).thenReturn(expiry);
        when(jwtService.generateToken(EMAIL, "VENDOR", expiry)).thenReturn("google-jwt");

        ApiResponse<LoginResponse> response = userService.loginGoogle(googleRequest(), "VENDOR");

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getToken()).isEqualTo("google-jwt");
    }

    @Test
    void bindsGoogleAccountToMatchingActiveLocalUser() {
        mockValidAuthorization();
        when(jwtService.getRole(TOKEN)).thenReturn("VENDOR");
        Map<String, Object> profile = activeUser("VENDOR");
        profile.put("provider", "LOCAL");
        profile.put("googleSub", null);
        when(userRepository.findProfileByEmail(EMAIL)).thenReturn(Optional.of(profile));
        when(authService.verifyGoogleCredential("google-credential")).thenReturn(googleTokenInfo());
        when(userRepository.findGoogleUserBySub("google-sub")).thenReturn(Optional.empty());
        when(userRepository.bindGoogleAccountByEmail(EMAIL, "google-sub")).thenReturn(1);

        ApiResponse<Void> response = userService.bindGoogle(AUTHORIZATION, googleRequest());

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).bindGoogleAccountByEmail(EMAIL, "google-sub");
    }

    @Test
    void rejectsGoogleBindingWithoutAuthorization() {
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);

        assertThat(userService.bindGoogle(null, googleRequest()).isSuccessStatus()).isFalse();
        verify(authService, never()).verifyGoogleCredential(anyString());
    }

    @Test
    void logsOutAndRevokesJwt() {
        mockValidAuthorization();

        ApiResponse<Void> response = userService.logout(AUTHORIZATION);

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).markLogoutByEmail(EMAIL);
        verify(jwtService).revokeToken(TOKEN);
    }

    @Test
    void autoLogoutWritesRequestAndStatusLogForEveryExpiredUser() {
        when(userRepository.autoLogoutExpiredUsersAndReturnIds()).thenReturn(List.of(10L, 20L));
        when(requestLogRepository.createRequestLog(null, "SYSTEM", "AUTO_LOGOUT_EXPIRED_USERS", 200))
                .thenReturn(101L, 102L);

        userService.autoLogout();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StatusLogEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(statusLogRepository, org.mockito.Mockito.times(2)).createStatusLogs(anyLong(), entries.capture());
        assertThat(entries.getAllValues())
                .extracting(list -> list.get(0).getTargetId())
                .containsExactly(10L, 20L);
    }

    @Test
    void returnsCurrentUserOrNotFound() {
        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION)).thenReturn(TOKEN);
        when(jwtService.getEmail(TOKEN)).thenReturn(EMAIL);
        when(userRepository.findProfileByEmail(EMAIL))
                .thenReturn(Optional.of(activeUser("VENDOR")), Optional.empty());

        ApiResponse<UserProfileResponse> found = userService.getCurrentUser(AUTHORIZATION);
        ApiResponse<UserProfileResponse> missing = userService.getCurrentUser(AUTHORIZATION);

        assertThat(found.isSuccessStatus()).isTrue();
        assertThat(found.getData().getUser().get("email")).isEqualTo(EMAIL);
        assertThat(missing.isSuccessStatus()).isFalse();
    }

    @Test
    void deactivatesVendorWithoutActiveApplications() {
        mockValidAuthorization();
        when(jwtService.getRole(TOKEN)).thenReturn("VENDOR");
        when(userRepository.findProfileByEmail(EMAIL)).thenReturn(Optional.of(activeUser("VENDOR")));
        when(userRepository.existsActiveVendorApplication(10L)).thenReturn(false);
        when(userRepository.deactivateUserById(10L)).thenReturn(1);

        ApiResponse<Void> response = userService.deactivateCurrentAccount(AUTHORIZATION);

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).deactivateUserById(10L);
    }

    @Test
    void preventsDeactivationWhileRoleHasActiveWork() {
        mockValidAuthorization();
        when(jwtService.getRole(TOKEN)).thenReturn("VENDOR");
        when(userRepository.findProfileByEmail(EMAIL)).thenReturn(Optional.of(activeUser("VENDOR")));
        when(userRepository.existsActiveVendorApplication(10L)).thenReturn(true);

        assertThat(userService.deactivateCurrentAccount(AUTHORIZATION).isSuccessStatus()).isFalse();
        verify(userRepository, never()).deactivateUserById(anyLong());
    }

    @Test
    void verifiesAccountEmailAndConsumesCode() {
        Map<String, Object> token = tokenData(51L, 10L, LocalDateTime.now().plusMinutes(5));
        when(userRepository.findVerificationCode(EMAIL, "123456", UserRepository.TOKEN_TYPE_EMAIL_VERIFY))
                .thenReturn(Optional.of(token));

        ApiResponse<Void> response = userService.verifyCreateAccountEmail(emailVerificationRequest());

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).markEmailVerified(10L);
        verify(userRepository).deleteUserToken(51L, UserRepository.TOKEN_TYPE_EMAIL_VERIFY);
    }

    @Test
    void rejectsExpiredAccountVerificationCode() {
        Map<String, Object> token = tokenData(51L, 10L, LocalDateTime.now().minusMinutes(1));
        when(userRepository.findVerificationCode(EMAIL, "123456", UserRepository.TOKEN_TYPE_EMAIL_VERIFY))
                .thenReturn(Optional.of(token));

        assertThat(userService.verifyCreateAccountEmail(emailVerificationRequest()).isSuccessStatus()).isFalse();
        verify(userRepository, never()).markEmailVerified(anyLong());
    }

    @Test
    void requestsPasswordResetWithoutRevealingWhetherEmailExists() {
        RequestPasswordResetRequest request = new RequestPasswordResetRequest();
        request.setEmail(EMAIL);
        when(userRepository.findLocalUserByEmail(EMAIL))
                .thenReturn(Optional.of(Map.of("id", 10L)), Optional.empty());

        ApiResponse<Void> existing = userService.requestPasswordReset(request);
        ApiResponse<Void> missing = userService.requestPasswordReset(request);

        assertThat(existing.isSuccessStatus()).isTrue();
        assertThat(missing.isSuccessStatus()).isTrue();
        verify(emailService).sendPasswordResetCode(eq(EMAIL), anyString());
    }

    @Test
    void verifiesPasswordResetCodeAndReplacesItWithHashedResetToken() {
        Map<String, Object> token = tokenData(61L, 10L, LocalDateTime.now().plusMinutes(5));
        when(userRepository.findVerificationCode(EMAIL, "123456", UserRepository.TOKEN_TYPE_PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(userRepository.deleteUserToken(61L, UserRepository.TOKEN_TYPE_PASSWORD_RESET)).thenReturn(1);

        ApiResponse<PasswordResetVerificationResponse> response =
                userService.verifyResetPasswordEmail(emailVerificationRequest());

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getResetToken()).isNotBlank();
        verify(userRepository).createUserToken(
                eq(10L),
                eq(sha256(response.getData().getResetToken())),
                eq(UserRepository.TOKEN_TYPE_PASSWORD_RESET),
                any(LocalDateTime.class));
    }

    @Test
    void resetsPasswordWithOneTimeResetToken() {
        ResetPasswordRequest request = resetPasswordRequest("plain-reset-token");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        Map<String, Object> token = tokenData(71L, 10L, LocalDateTime.now().plusMinutes(5));
        when(jwtService.extractTokenFromAuthorizationHeader(null)).thenReturn(null);
        when(userRepository.findUserToken(sha256("plain-reset-token"), UserRepository.TOKEN_TYPE_PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(userRepository.deleteUserToken(71L, UserRepository.TOKEN_TYPE_PASSWORD_RESET)).thenReturn(1);
        when(authService.hashPassword("newPassword1")).thenReturn("new-hash");
        when(userRepository.updateLocalPasswordByUserId(10L, "new-hash")).thenReturn(1);
        when(userRepository.findUserAccountById(10L)).thenReturn(Optional.of(Map.of(
                "id", 10L, "email", EMAIL, "role", "VENDOR", "status", "ACTIVE")));

        ApiResponse<Void> response;
        try {
            response = userService.resetPassword(null, request);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(servletRequest.getAttribute(RequestLogService.RESOLVED_USER_ID_ATTRIBUTE)).isEqualTo(10L);
        verify(userRepository).updateLocalPasswordByUserId(10L, "new-hash");
        verify(notificationService).notifyPasswordResetCompleted(eq(10L), eq(EMAIL), anyString());
    }

    @Test
    void resetsPasswordForCurrentValidLoginSession() {
        ResetPasswordRequest request = resetPasswordRequest(null);
        request.setCurrentPassword("currentPassword1");
        mockValidAuthorization();
        when(updateActiveTimeService.isCurrentLoginSession(TOKEN)).thenReturn(true);
        when(userRepository.findLocalUserByEmail(EMAIL))
                .thenReturn(Optional.of(Map.of("id", 10L, "password_hash", "current-hash")));
        when(authService.matchesPassword("currentPassword1", "current-hash")).thenReturn(true);
        when(authService.hashPassword("newPassword1")).thenReturn("new-hash");
        when(userRepository.updateLocalPasswordByEmail(EMAIL, "new-hash")).thenReturn(1);

        ApiResponse<Void> response = userService.resetPassword(AUTHORIZATION, request);

        assertThat(response.isSuccessStatus()).isTrue();
        verify(userRepository).updateLocalPasswordByEmail(EMAIL, "new-hash");
        verify(notificationService).notifyPasswordResetCompleted(eq(10L), eq(EMAIL), anyString());
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordIsMissing() {
        ResetPasswordRequest request = resetPasswordRequest(null);
        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION)).thenReturn(TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(updateActiveTimeService.isCurrentLoginSession(TOKEN)).thenReturn(true);

        ApiResponse<Void> response = userService.resetPassword(AUTHORIZATION, request);

        assertThat(response.isSuccessStatus()).isFalse();
        verify(userRepository, never()).updateLocalPasswordByEmail(anyString(), anyString());
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordIsIncorrect() {
        ResetPasswordRequest request = resetPasswordRequest(null);
        request.setCurrentPassword("wrongPassword1");
        mockValidAuthorization();
        when(updateActiveTimeService.isCurrentLoginSession(TOKEN)).thenReturn(true);
        when(userRepository.findLocalUserByEmail(EMAIL))
                .thenReturn(Optional.of(Map.of("password_hash", "current-hash")));
        when(authService.matchesPassword("wrongPassword1", "current-hash")).thenReturn(false);

        ApiResponse<Void> response = userService.resetPassword(AUTHORIZATION, request);

        assertThat(response.isSuccessStatus()).isFalse();
        verify(userRepository, never()).updateLocalPasswordByEmail(anyString(), anyString());
    }

    private void mockValidAuthorization() {
        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION)).thenReturn(TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.getEmail(TOKEN)).thenReturn(EMAIL);
    }

    private LocalRegisterRequest localRegisterRequest() {
        LocalRegisterRequest request = new LocalRegisterRequest();
        request.setName("測試攤主");
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        return request;
    }

    private LocalLoginRequest localLoginRequest() {
        LocalLoginRequest request = new LocalLoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        return request;
    }

    private GoogleCredentialRequest googleRequest() {
        GoogleCredentialRequest request = new GoogleCredentialRequest();
        request.setCredential("google-credential");
        return request;
    }

    private GoogleTokenInfo googleTokenInfo() {
        GoogleTokenInfo tokenInfo = new GoogleTokenInfo();
        tokenInfo.setSub("google-sub");
        tokenInfo.setAud(GOOGLE_CLIENT_ID);
        tokenInfo.setEmail(EMAIL);
        tokenInfo.setEmail_verified("true");
        tokenInfo.setName("Google User");
        return tokenInfo;
    }

    private Map<String, Object> activeUser(String role) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", 10L);
        user.put("email", EMAIL);
        user.put("password_hash", "stored-hash");
        user.put("name", "測試使用者");
        user.put("role", role);
        user.put("provider", "LOCAL");
        user.put("googleSub", null);
        user.put("status", "ACTIVE");
        user.put("isLogin", false);
        user.put("emailVerifiedAt", LocalDateTime.now().minusDays(1));
        return user;
    }

    private EmailVerificationRequest emailVerificationRequest() {
        EmailVerificationRequest request = new EmailVerificationRequest();
        request.setEmail(EMAIL);
        request.setCode("123456");
        return request;
    }

    private ResetPasswordRequest resetPasswordRequest(String resetToken) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setResetToken(resetToken);
        request.setPassword("newPassword1");
        return request;
    }

    private Map<String, Object> tokenData(Long tokenId, Long userId, LocalDateTime expiresAt) {
        Map<String, Object> token = new HashMap<>();
        token.put("id", tokenId);
        token.put("user_id", userId);
        token.put("expires_at", Timestamp.valueOf(expiresAt));
        token.put("email_verified_at", null);
        return token;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
