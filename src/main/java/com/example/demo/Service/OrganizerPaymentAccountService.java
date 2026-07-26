package com.example.demo.Service;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.demo.Repository.OrganizerPaymentAccountRepository;
import com.example.demo.dto.request.OrganizerPaymentAccountRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.OrganizerNewebPayAccountLoadResponse;
import com.example.demo.dto.response.OrganizerNewebPayPortalResponse;
import com.example.demo.dto.response.OrganizerNewebPayVerificationPaymentResponse;
import com.example.demo.dto.response.OrganizerPaymentAccountResponse;

@Service
public class OrganizerPaymentAccountService {
    private static final Pattern MERCHANT_ID = Pattern.compile("^[A-Za-z0-9_-]{5,50}$");

    private final OrganizerPaymentAccountRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final JwtService jwtService;

    @Autowired
    private NewebPayService newebPayService;

    public OrganizerPaymentAccountService(
            OrganizerPaymentAccountRepository repository,
            CredentialEncryptionService encryptionService,
            JwtService jwtService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.jwtService = jwtService;
    }

    @Transactional
    public ApiResponse<OrganizerPaymentAccountResponse> save(
            String authorizationHeader, OrganizerPaymentAccountRequest request) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        String validation = validate(request);
        if (validation != null) {
            return ApiResponse.fail(validation);
        }

        String merchantId = request.merchantId().trim();
        Long organizerProfileId = number(organizer.get("organizerProfileId"));
        Map<String, Object> existingAccount = repository
                .findByOrganizerProfileId(organizerProfileId)
                .orElse(null);
        if (existingAccount != null
                && !merchantId.equals(text(existingAccount.get("merchantId")))
                && repository.hasPayments(number(existingAccount.get("paymentAccountId")))) {
            return ApiResponse.fail(
                    409,
                    "此藍新帳戶已有付款紀錄，不可更換 MerchantID");
        }
        String encryptedHashKey = encryptionService.encrypt(request.hashKey().trim());
        String encryptedHashIv = encryptionService.encrypt(request.hashIv().trim());
        // 藍新沒有提供不建立交易即可驗證 HashKey/HashIV 的通用 API。
        // 此處完成格式與加解密自我驗證；真正有效性會由第一筆交易/查詢驗證。
        if (!request.hashKey().trim().equals(encryptionService.decrypt(encryptedHashKey))
                || !request.hashIv().trim().equals(encryptionService.decrypt(encryptedHashIv))) {
            throw new IllegalStateException("藍新金流金鑰加密驗證失敗");
        }
        Long paymentAccountId = repository.upsert(
                organizerProfileId, merchantId, encryptedHashKey, encryptedHashIv);
        repository.bindUnboundEvents(number(organizer.get("userId")), paymentAccountId);
        return get(authorizationHeader);
    }

    public ApiResponse<OrganizerPaymentAccountResponse> get(String authorizationHeader) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        Map<String, Object> account = repository
                .findByOrganizerProfileId(number(organizer.get("organizerProfileId")))
                .orElse(null);
        if (account == null) {
            return ApiResponse.fail(404, "尚未綁定藍新商店");
        }
        return ApiResponse.success(
                "藍新商店綁定資料載入成功",
                new OrganizerPaymentAccountResponse(
                        maskMerchantId(text(account.get("merchantId"))),
                        text(account.get("status")),
                        dateTime(account.get("updatedAt"))));
    }

    public ApiResponse<OrganizerNewebPayPortalResponse> portal(String authorizationHeader) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        return ApiResponse.success(
                "藍新金流入口網址載入成功",
                new OrganizerNewebPayPortalResponse(
                        "https://www.newebpay.com/main/registration",
                        "https://www.newebpay.com/main/login_center/single_login"));
    }

    public ApiResponse<OrganizerNewebPayAccountLoadResponse> load(String authorizationHeader) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        Map<String, Object> account = repository
                .findByOrganizerProfileId(number(organizer.get("organizerProfileId")))
                .orElse(null);
        if (account == null) {
            return ApiResponse.success(
                    "目前尚未綁定藍新商店",
                    new OrganizerNewebPayAccountLoadResponse(
                            false, null, "", "", null, "UNVERIFIED", null, null));
        }
        return ApiResponse.success(
                "藍新商店設定載入成功",
                new OrganizerNewebPayAccountLoadResponse(
                        true,
                        text(account.get("merchantId")),
                        "",
                        "",
                        text(account.get("status")),
                        text(account.get("verificationStatus")),
                        dateTime(account.get("verifiedAt")),
                        dateTime(account.get("updatedAt"))));
    }

    @Transactional
    public ApiResponse<OrganizerNewebPayVerificationPaymentResponse> verify(String authorizationHeader) {
        Map<String, Object> organizer = authenticatedOrganizer(authorizationHeader);
        if (organizer.containsKey("message")) {
            return ApiResponse.fail(organizer.get("message").toString());
        }
        Map<String, Object> account = repository
                .findByOrganizerProfileId(number(organizer.get("organizerProfileId")))
                .orElse(null);
        if (account == null) {
            return ApiResponse.fail(404, "尚未綁定藍新商店");
        }
        Long paymentAccountId = number(account.get("paymentAccountId"));
        String verificationNo = generateVerificationNo();
        BigDecimal amount = BigDecimal.ONE;
        try {
            repository.createVerification(paymentAccountId, verificationNo);
            return ApiResponse.success(
                    "藍新商店 NT$1 驗證付款建立成功",
                    newebPayService.createPaymentAccountVerification(
                            account, verificationNo, amount));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("建立藍新商店驗證付款失敗", exception);
        }
    }

    private String generateVerificationNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 4).toUpperCase();
        return "NPV" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + suffix;
    }

    private Map<String, Object> authenticatedOrganizer(String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return Map.of("message", "請先登入後再操作");
        }
        if (!jwtService.isTokenValid(token)) {
            return Map.of("message", "登入資訊無效或已過期，請重新登入");
        }
        if (!"ORGANIZER".equals(jwtService.getRole(token))) {
            return Map.of("message", "目前帳號不是主辦方帳號");
        }
        Map<String, Object> organizer = repository.findOrganizerByEmail(jwtService.getEmail(token)).orElse(null);
        if (organizer == null) {
            return Map.of("message", "找不到主辦方資料");
        }
        if (!"ACTIVE".equals(text(organizer.get("userStatus")))) {
            return Map.of("message", "主辦方帳號尚未啟用或已停用");
        }
        return organizer;
    }

    private String validate(OrganizerPaymentAccountRequest request) {
        if (request == null) return "請填寫藍新商店資料";
        if (request.merchantId() == null || !MERCHANT_ID.matcher(request.merchantId().trim()).matches()) {
            return "MerchantID 格式不正確";
        }
        if (request.hashKey() == null || request.hashKey().trim().length() != 32) {
            return "HashKey 必須為 32 個字元";
        }
        if (request.hashIv() == null || request.hashIv().trim().length() != 16) {
            return "HashIV 必須為 16 個字元";
        }
        return null;
    }

    private String maskMerchantId(String value) {
        if (value == null || value.length() <= 6) return "***";
        return value.substring(0, 5) + "***" + value.substring(value.length() - 3);
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }
}
