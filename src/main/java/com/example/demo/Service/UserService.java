package com.example.demo.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.StatusLogRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.log.StatusLogEntry;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.request.EmailVerificationRequest;
import com.example.demo.dto.request.GoogleCredentialRequest;
import com.example.demo.dto.request.LocalLoginRequest;
import com.example.demo.dto.request.LocalRegisterRequest;
import com.example.demo.dto.request.RequestPasswordResetRequest;
import com.example.demo.dto.request.ResendRegistrationVerificationRequest;
import com.example.demo.dto.request.ResetPasswordRequest;
import com.example.demo.dto.response.GoogleTokenInfo;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.LoginUserResponse;
import com.example.demo.dto.response.PasswordResetVerificationResponse;
import com.example.demo.dto.response.UserProfileResponse;
import com.example.demo.dto.response.UserResponse;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @Autowired
    private StatusLogRepository statusLogRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UpdateActiveTimeService updateActiveTimeService;

    @Autowired
    private EmailService emailService;

    @Value("${google.client-id}")
    private String googleClientId;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public ApiResponse<List<UserResponse>> findAllUsers() {
        return ApiResponse.success("Users retrieved successfully", userRepository.findAllUsers());
    }

    @Transactional
    public ApiResponse<Void> registerLocal(LocalRegisterRequest user, String role) {
        if (isAdminRole(role)) {
            return ApiResponse.fail("Admin accounts must be created by the system");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            return ApiResponse.fail("Email already registered");
        }

        Long userId = userRepository.createLocalUser(
                role,
                user.getEmail(),
                authService.hashPassword(user.getPassword()));
        userRepository.createUserProfile(userId, role, user.getName(), user.getEmail());

        String verificationCode = generateVerificationCode();
        userRepository.deleteEmailVerificationTokensByUserId(userId);
        userRepository.createEmailVerificationToken(userId, verificationCode, LocalDateTime.now().plusMinutes(10));
        emailService.sendVerificationCode(user.getEmail(), verificationCode);
        return ApiResponse.success("User registered successfully. Verification code has been sent to email");
    }

    @Transactional
    public ApiResponse<Void> registerGoogle(GoogleCredentialRequest body, String role) {
        if (isAdminRole(role)) {
            return ApiResponse.fail("Admin accounts do not support Google registration");
        }
        String credential = body.getCredential();
        if (credential == null || credential.isBlank()) {
            return ApiResponse.fail("Google credential is required");
        }

        GoogleTokenInfo tokenInfo = authService.verifyGoogleCredential(credential);
        if (tokenInfo == null) {
            return ApiResponse.fail("Invalid Google credential");
        }

        String validationMessage = validateGoogleTokenInfo(tokenInfo);
        if (validationMessage != null) {
            return ApiResponse.fail(validationMessage);
        }

        if (userRepository.existsByEmail(tokenInfo.getEmail())) {
            return ApiResponse.fail("Google account has already register");
        }

        Long userId = userRepository.createGoogleUser(
                role,
                tokenInfo.getEmail(),
                tokenInfo.getSub());
        userRepository.createUserProfile(userId, role, tokenInfo.getName(), tokenInfo.getEmail());

        String verificationCode = generateVerificationCode();
        userRepository.deleteEmailVerificationTokensByUserId(userId);
        userRepository.createEmailVerificationToken(userId, verificationCode, LocalDateTime.now().plusMinutes(10));
        emailService.sendVerificationCode(tokenInfo.getEmail(), verificationCode);

        return ApiResponse.success("Google user registered successfully. Verification code has been sent to email");
    }

    @Transactional
    public ApiResponse<LoginResponse> loginLocal(LocalLoginRequest body, String expectedRole) {
        Optional<Map<String, Object>> userData = userRepository.findLocalUserByEmail(body.getEmail());

        if (userData.isEmpty()
                || !authService.matchesPassword(body.getPassword(), (String) userData.get().get("password_hash"))) {
            return ApiResponse.fail("Invalid email or password");
        }
        if (userData.get().get("emailVerifiedAt") == null) {
            return ApiResponse.fail("Email is not verified");
        }
        if (!"ACTIVE".equals(userData.get().get("status"))) {
            return ApiResponse.fail("Account is not active or disabled");
        }
        String role = userData.get().get("role").toString();

        if (!expectedRole.equals(role)) {//如果該登入者的帳號與密碼不符合其身分(ex:用攤主的資訊來登入主辦帳號)
            return ApiResponse.fail("This account cannot login from this portal");
        }

        LocalDateTime sessionExpiresAt = userRepository.startLoginSession(
                toLong(userData.get().get("id")),
                jwtService.calculateExpiration());
        if (sessionExpiresAt == null) {
            return ApiResponse.fail("Login status update failed");
        }
        userData.get().put("isLogin", true);

        return buildLoginResponse(
                userData.get(),
                "Login successful",
                sessionExpiresAt);
    }

    @Transactional
    public ApiResponse<LoginResponse> loginGoogle(GoogleCredentialRequest body, String expectedRole) {
        if (isAdminRole(expectedRole)) {
            return ApiResponse.fail("Admin accounts do not support Google login");
        }
        String credential = body.getCredential();
        if (credential == null || credential.isBlank()) {
            return ApiResponse.fail("Google credential is required");
        }

        GoogleTokenInfo tokenInfo = authService.verifyGoogleCredential(credential);
        if (tokenInfo == null) {
            return ApiResponse.fail("Invalid Google credential");
        }

        String validationMessage = validateGoogleTokenInfo(tokenInfo);
        if (validationMessage != null) {
            return ApiResponse.fail(validationMessage);
        }
        Optional<Map<String, Object>> userData = userRepository.findGoogleUserBySub(tokenInfo.getSub());
        if (userData.isEmpty()) {
            return ApiResponse.fail("Google account is not registered");
        }
        if (userData.get().get("emailVerifiedAt") == null) {
            return ApiResponse.fail("Email is not verified");
        }
        if (!"ACTIVE".equals(userData.get().get("status"))) {
            return ApiResponse.fail("Account is not active");
        }

        //比對身分與登入帳號者
        String role = userData.get().get("role").toString();
        if (!expectedRole.equals(role)) {
            return ApiResponse.fail("This account cannot login from this portal");
        }

        LocalDateTime sessionExpiresAt = userRepository.startLoginSession(
                toLong(userData.get().get("id")),
                jwtService.calculateExpiration());
        if (sessionExpiresAt == null) {
            return ApiResponse.fail("Login status update failed");
        }
        userData.get().put("isLogin", true);

        return buildLoginResponse(
                userData.get(),
                "Google login successful",
                sessionExpiresAt);
    }

    @Transactional
    public ApiResponse<Void> bindGoogle(String authorizationHeader, GoogleCredentialRequest body) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }

        String email = jwtService.getEmail(token);
        String tokenRole = jwtService.getRole(token);
        Map<String, Object> user = userRepository.findProfileByEmail(email)
                .orElse(null);
        if (user == null) {
            return ApiResponse.fail("User not found");
        }
        if (!tokenRole.equals(user.get("role").toString())) {
            return ApiResponse.fail("Account role does not match token role");
        }
        if (!"ACTIVE".equals(user.get("status"))) {
            return ApiResponse.fail("Account is not active");
        }

        String provider = user.get("provider").toString();
        Object currentGoogleSub = user.get("googleSub");
        if (!"LOCAL".equals(provider) || currentGoogleSub != null) {
            return ApiResponse.fail("Google account is already bound");
        }

        String credential = body.getCredential();
        if (credential == null || credential.isBlank()) {
            return ApiResponse.fail("Google credential is required");
        }

        GoogleTokenInfo tokenInfo = authService.verifyGoogleCredential(credential);
        if (tokenInfo == null) {
            return ApiResponse.fail("Invalid Google credential");
        }

        String validationMessage = validateGoogleTokenInfo(tokenInfo);
        if (validationMessage != null) {
            return ApiResponse.fail(validationMessage);
        }
        if (!email.equalsIgnoreCase(tokenInfo.getEmail())) {
            return ApiResponse.fail("Google email does not match current account");
        }

        Optional<Map<String, Object>> boundGoogleUser = userRepository.findGoogleUserBySub(tokenInfo.getSub());
        if (boundGoogleUser.isPresent()) {
            return ApiResponse.fail("Google account is already bound");
        }

        int boundRows = userRepository.bindGoogleAccountByEmail(email, tokenInfo.getSub());
        if (boundRows == 0) {
            return ApiResponse.fail("Google account binding failed");
        }

        return ApiResponse.success("Google account bound successfully");
    }

    public ApiResponse<Void> logout(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }

        String email = jwtService.getEmail(token);
        userRepository.markLogoutByEmail(email);
        //將token放入失效名單
        jwtService.revokeToken(token);
        return ApiResponse.success("Logout successful");
    }

    @Scheduled(fixedRate = 60_000)
    public void autoLogout() {
        userRepository.autoLogoutExpiredUsersAndReturnIds()
                .forEach(this::recordAutoLogoutStatus);
    }

    private void recordAutoLogoutStatus(Long userId) {
        Long requestLogId = requestLogRepository.createRequestLog(
                null,
                "SYSTEM",
                "AUTO_LOGOUT_EXPIRED_USERS",
                200);
        statusLogRepository.createStatusLogs(
                requestLogId,
                List.of(new StatusLogEntry(
                        requestLogId,
                        "USER",
                        userId,
                        "users.isLogin",
                        "false")));
    }

    public ApiResponse<UserProfileResponse> getCurrentUser(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);

        String email = jwtService.getEmail(token);
        return userRepository.findProfileByEmail(email)
                .map(user -> ApiResponse.success("User info retrieved successfully", new UserProfileResponse(user)))
                .orElseGet(() -> ApiResponse.fail("User not found"));
    }

    @Transactional
    public ApiResponse<Void> deactivateCurrentAccount(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }

        String email = jwtService.getEmail(token);
        String tokenRole = jwtService.getRole(token);
        Map<String, Object> user = userRepository.findProfileByEmail(email)
                .orElse(null);
        if (user == null) {
            return ApiResponse.fail("User not found");
        }

        String role = user.get("role").toString();
        if (!role.equals(tokenRole)) {
            return ApiResponse.fail("Account role does not match token role");
        }
        if (!"ACTIVE".equals(user.get("status"))) {
            return ApiResponse.fail("Account is not active");
        }
        //根據role先判斷是否該帳號還有連結的服務沒有完成
        Long userId = ((Number) user.get("id")).longValue();
        if ("VENDOR".equals(role)) {
            if (userRepository.existsActiveVendorApplication(userId)) {
                return ApiResponse.fail("Account cannot be deactivated while vendor applications are still in progress");
            }
        } else if ("ORGANIZER".equals(role)) {
            if (userRepository.existsActiveOrganizerEvent(userId)) {
                return ApiResponse.fail("Account cannot be deactivated while organizer events are still in progress");
            }
        } else {
            return ApiResponse.fail("This account role cannot be deactivated from this API");
        }

        int updatedRows = userRepository.deactivateUserById(userId);
        if (updatedRows == 0) {
            return ApiResponse.fail("Account deactivation failed");
        }

        return ApiResponse.success("Account deactivated successfully");
    }

    public ApiResponse<Void> verifyCreateAccountEmail(EmailVerificationRequest body) {
        ApiResponse<Void> validationError = validateVerificationRequest(body);
        //確認輸入沒有錯誤(email+code)
        if (validationError != null) {
            return validationError;
        }

        Optional<Map<String, Object>> tokenData = findValidVerificationToken(
                body,
                UserRepository.TOKEN_TYPE_EMAIL_VERIFY);
        if (tokenData.isEmpty()) {
            return ApiResponse.fail("Invalid or expired verification code");
        }

        Map<String, Object> token = tokenData.get();
        if (token.get("email_verified_at") != null) {
            return ApiResponse.fail("Email already verified");
        }

        Long userId = ((Number) token.get("user_id")).longValue();
        Long tokenId = ((Number) token.get("id")).longValue();
        userRepository.markEmailVerified(userId);
        userRepository.deleteUserToken(tokenId, UserRepository.TOKEN_TYPE_EMAIL_VERIFY);

        return ApiResponse.success("Email verified successfully");
    }

    @Transactional
    public ApiResponse<Void> resendCreateAccountVerificationCode(
            ResendRegistrationVerificationRequest body) {
        Optional<Map<String, Object>> userData = userRepository.findLocalUserByEmail(body.getEmail());
        if (userData.isEmpty()) {
            return ApiResponse.fail("Local account not found");
        }

        Map<String, Object> user = userData.get();
        if (user.get("emailVerifiedAt") != null) {
            return ApiResponse.fail("Email already verified");
        }

        Long userId = ((Number) user.get("id")).longValue();
        String verificationCode = generateVerificationCode();
        userRepository.deleteEmailVerificationTokensByUserId(userId);
        userRepository.createEmailVerificationToken(
                userId,
                verificationCode,
                LocalDateTime.now().plusMinutes(10));
        emailService.sendVerificationCode(body.getEmail(), verificationCode);

        return ApiResponse.success("Registration verification code has been sent");
    }

    public ApiResponse<Void> requestPasswordReset(RequestPasswordResetRequest body) {
        Optional<Map<String, Object>> userData = userRepository.findLocalUserByEmail(body.getEmail());
        if (userData.isPresent()) {
            Long userId = ((Number) userData.get().get("id")).longValue();
            String verificationCode = generateVerificationCode();
            userRepository.deleteUserTokensByUserId(
                    userId,
                    UserRepository.TOKEN_TYPE_PASSWORD_RESET);
            userRepository.createUserToken(
                    userId,
                    verificationCode,
                    UserRepository.TOKEN_TYPE_PASSWORD_RESET,
                    LocalDateTime.now().plusMinutes(10));
            emailService.sendPasswordResetCode(body.getEmail(), verificationCode);
        }

        return ApiResponse.success("If the email belongs to a local account, a verification code has been sent");
    }

    @Transactional
    public ApiResponse<PasswordResetVerificationResponse> verifyResetPasswordEmail(EmailVerificationRequest body) {
        ApiResponse<Void> validationError = validateVerificationRequest(body);
        if (validationError != null) {
            return ApiResponse.fail(validationError.getMessage());
        }

        Optional<Map<String, Object>> tokenData = findValidVerificationToken(
                body,
                UserRepository.TOKEN_TYPE_PASSWORD_RESET);
        if (tokenData.isEmpty()) {
            return ApiResponse.fail("Invalid or expired verification code");
        }

        Map<String, Object> verificationToken = tokenData.get();
        Long userId = ((Number) verificationToken.get("user_id")).longValue();
        Long tokenId = ((Number) verificationToken.get("id")).longValue();
        String resetToken = generateResetToken();

        int consumedTokens = userRepository.deleteUserToken(
                tokenId,
                UserRepository.TOKEN_TYPE_PASSWORD_RESET);
        if (consumedTokens == 0) {
            return ApiResponse.fail("Invalid or expired verification code");
        }
        userRepository.createUserToken(
                userId,
                hashResetToken(resetToken),
                UserRepository.TOKEN_TYPE_PASSWORD_RESET,
                LocalDateTime.now().plusMinutes(10));

        return ApiResponse.success(
                "Password reset email verified successfully",
                new PasswordResetVerificationResponse(resetToken));
    }

    @Transactional
    public ApiResponse<Void> resetPassword(String authorizationHeader, ResetPasswordRequest body) {
        if (body.getPassword() == null || body.getPassword().isBlank()) {
            return ApiResponse.fail("Password is required");
        }

        String loginToken = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (loginToken != null && !loginToken.isBlank()) {
            return resetPasswordByCurrentLogin(loginToken, body.getPassword());
        }

        if (body.getResetToken() == null || body.getResetToken().isBlank()) {
            return ApiResponse.fail("Reset token or Authorization token is required");
        }
        Optional<Map<String, Object>> tokenData = userRepository.findUserToken(
                hashResetToken(body.getResetToken()),
                UserRepository.TOKEN_TYPE_PASSWORD_RESET);
        if (tokenData.isEmpty()) {
            return ApiResponse.fail("Invalid or expired reset token");
        }

        Map<String, Object> resetToken = tokenData.get();
        LocalDateTime expiresAt = toLocalDateTime(resetToken.get("expires_at"));
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            return ApiResponse.fail("Invalid or expired reset token");
        }

        Long userId = ((Number) resetToken.get("user_id")).longValue();
        Long tokenId = ((Number) resetToken.get("id")).longValue();
        int consumedTokens = userRepository.deleteUserToken(
                tokenId,
                UserRepository.TOKEN_TYPE_PASSWORD_RESET);
        if (consumedTokens == 0) {
            return ApiResponse.fail("Invalid or expired reset token");
        }

        int updatedRows = userRepository.updateLocalPasswordByUserId(
                userId,
                authService.hashPassword(body.getPassword()));
        if (updatedRows == 0) {
            return ApiResponse.fail("Password reset failed");
        }

        return ApiResponse.success("Password reset successfully");
    }

    private ApiResponse<Void> resetPasswordByCurrentLogin(String token, String password) {
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }
        if (!updateActiveTimeService.isCurrentLoginSession(token)) {
            return ApiResponse.fail("Session expired");
        }

        int updatedRows = userRepository.updateLocalPasswordByEmail(
                jwtService.getEmail(token),
                authService.hashPassword(password));
        if (updatedRows == 0) {
            return ApiResponse.fail("Password reset failed");
        }

        return ApiResponse.success("Password reset successfully");
    }

    private String generateVerificationCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String generateResetToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashResetToken(String resetToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(resetToken.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private ApiResponse<Void> validateVerificationRequest(EmailVerificationRequest body) {
        if (body.getEmail() == null || body.getEmail().isBlank()) {
            return ApiResponse.fail("Email is required");
        }
        if (body.getCode() == null || !body.getCode().matches("\\d{6}")) {
            return ApiResponse.fail("6-digit verification code is required");
        }
        return null;
    }

    private Optional<Map<String, Object>> findValidVerificationToken(
            EmailVerificationRequest body,
            String tokenType) {
        Optional<Map<String, Object>> tokenData = userRepository.findVerificationCode(
                body.getEmail(),
                body.getCode(),
                tokenType);
        if (tokenData.isEmpty()) {
            return Optional.empty();
        }

        LocalDateTime expiresAt = toLocalDateTime(tokenData.get().get("expires_at"));
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        return tokenData;
    }

    private String validateGoogleTokenInfo(GoogleTokenInfo tokenInfo) {
        if (tokenInfo.getSub() == null || tokenInfo.getSub().isBlank()) {
            return "Invalid Google credential";
        }

        if (tokenInfo.getEmail() == null || tokenInfo.getEmail().isBlank()) {
            return "Invalid Google credential";
        }

        if (!googleClientId.equals(tokenInfo.getAud())) {
            return "Google client id does not match";
        }

        if (!Boolean.parseBoolean(tokenInfo.getEmail_verified())) {
            return "Google email is not verified";
        }

        return null;
    }

    private ApiResponse<LoginResponse> buildLoginResponse(
            Map<String, Object> userData,
            String message,
            LocalDateTime sessionExpiresAt) {
        String userEmail = userData.get("email").toString();
        String role = userData.get("role").toString();
        String token = jwtService.generateToken(userEmail, role, sessionExpiresAt);

        LoginUserResponse user = new LoginUserResponse(
                userEmail,
                userData.get("name"),
                role,
                userData.get("status"),
                userData.get("isLogin"));
        return ApiResponse.success(message, new LoginResponse(token, user));
    }

    private Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equals(role);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
