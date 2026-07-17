package com.example.demo.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Config.NewebPayProperties;
import com.example.demo.Repository.PaymentRepository;
import com.example.demo.dto.request.VendorPaymentRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NewebPayPaymentResponse;
import com.example.demo.dto.response.NewebPayQueryResponse;
import com.example.demo.dto.response.PaymentStatusResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class NewebPayService {

    private static final DateTimeFormatter PAYMENT_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter PAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private NewebPayProperties newebPayProperties;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Transactional
    public ApiResponse<NewebPayPaymentResponse> createPayment(
            String authorizationHeader,
            VendorPaymentRequest body) {
        if (body == null || body.getApplicationNo() == null || body.getApplicationNo().isBlank()) {
            return ApiResponse.fail("Application number is required");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        ApiResponse<Void> authError = validateVendorToken(token);
        if (authError != null) {
            return ApiResponse.fail(authError.getStatusCode(), authError.getMessage());
        }

        if (!isNewebPayConfigComplete()) {
            return ApiResponse.fail("NewebPay config is incomplete");
        }

        Map<String, Object> vendor = paymentRepository.findVendorPaymentUserByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (vendor == null) {
            return ApiResponse.fail("Vendor profile not found");
        }

        Map<String, Object> application = paymentRepository.findPayableApplication(body.getApplicationNo().trim())
                .orElse(null);
        if (application == null) {
            return ApiResponse.fail("Application not found");
        }

        Long vendorUserId = toLong(vendor.get("userId"));
        Long applicationUserId = toLong(application.get("userId"));
        if (vendorUserId == null || !vendorUserId.equals(applicationUserId)) {
            return ApiResponse.fail("Application does not belong to this account");
        }
        if (isTrue(application.get("isCancelled"))) {
            return ApiResponse.fail("Application has been cancelled");
        }
        if (!"APPROVED".equals(stringValue(application.get("reviewStatus")))) {
            return ApiResponse.fail("Application is not approved");
        }
        if ("PAID".equals(stringValue(application.get("paymentStatus")))) {
            return ApiResponse.fail("Application payment already paid");
        }

        Long applicationId = toLong(application.get("applicationId"));
        BigDecimal amount = toAmount(application.get("totalAmount"));
        if (!isValidNewebPayAmount(amount)) {
            return ApiResponse.fail("Payment amount is invalid");
        }

        Map<String, Object> pendingPayment = paymentRepository.findLatestPendingPayment(applicationId)
                .orElse(null);
        if (pendingPayment == null) {
            String newPaymentNo = generatePaymentNo();
            paymentRepository.createPendingPayment(newPaymentNo, applicationId, amount);
            pendingPayment = paymentRepository.findLatestPendingPayment(applicationId)
                    .orElseThrow(() -> new IllegalStateException("Payment record was not created"));
        }

        String paymentNo = stringValue(pendingPayment.get("paymentNo"));
        Long paymentId = toLong(pendingPayment.get("paymentId"));

        Map<String, String> tradeInfo = buildMpgTradeInfo(paymentNo, amount, application);
        String encryptedTradeInfo = encrypt(toQueryString(tradeInfo));
        String tradeSha = sha256Upper("HashKey=" + newebPayProperties.getHashKey()
                + "&" + encryptedTradeInfo
                + "&HashIV=" + newebPayProperties.getHashIv());

        return ApiResponse.success(
                "NewebPay payment created successfully",
                new NewebPayPaymentResponse(
                        applicationId,
                        stringValue(application.get("applicationNo")),
                        paymentId,
                        paymentNo,
                        paymentNo,
                        newebPayProperties.getGateway(),
                        newebPayProperties.getMerchantId(),
                        encryptedTradeInfo,
                        tradeSha,
                        newebPayProperties.getVersion()));
    }

    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            String authorizationHeader,
            String applicationNo) {
        if (applicationNo == null || applicationNo.isBlank()) {
            return ApiResponse.fail("Application number is required");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        ApiResponse<Void> authError = validateVendorToken(token);
        if (authError != null) {
            return ApiResponse.fail(authError.getStatusCode(), authError.getMessage());
        }

        Map<String, Object> payment = paymentRepository.findPaymentStatusByApplicationNo(applicationNo.trim())
                .orElse(null);
        if (payment == null) {
            return ApiResponse.fail("Application not found");
        }
        Long applicationUserId = toLong(payment.get("userId"));
        Long vendorUserId = toLong(paymentRepository.findVendorPaymentUserByEmail(jwtService.getEmail(token))
                .map(vendor -> vendor.get("userId"))
                .orElse(null));
        if (applicationUserId == null || vendorUserId == null || !applicationUserId.equals(vendorUserId)) {
            return ApiResponse.fail("Application does not belong to this account");
        }

        return ApiResponse.success(
                "Payment status retrieved successfully",
                toPaymentStatusResponse(payment));
    }

    @Transactional
    public ApiResponse<NewebPayQueryResponse> queryNewebPayTrade(
            String authorizationHeader,
            String applicationNo) {
        ApiResponse<PaymentStatusResponse> localStatus = getPaymentStatus(authorizationHeader, applicationNo);
        if (!localStatus.isSuccessStatus()) {
            return ApiResponse.fail(localStatus.getStatusCode(), localStatus.getMessage());
        }
        PaymentStatusResponse status = localStatus.getData();
        if (status.getPaymentNo() == null || status.getPaymentNo().isBlank()) {
            return ApiResponse.fail("Payment record not found");
        }
        if (!isNewebPayQueryConfigComplete()) {
            return ApiResponse.fail("NewebPay config is incomplete");
        }

        Map<String, String> checkValueSource = new LinkedHashMap<>();
        checkValueSource.put("Amt", toNewebPayAmount(status.getPaymentAmount()));
        checkValueSource.put("MerchantID", newebPayProperties.getMerchantId());
        checkValueSource.put("MerchantOrderNo", status.getPaymentNo());

        String checkValue = sha256Upper("IV=" + newebPayProperties.getHashIv()
                + "&" + toQueryString(checkValueSource)
                + "&Key=" + newebPayProperties.getHashKey());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("MerchantID", newebPayProperties.getMerchantId());
        form.put("Version", "1.3");
        form.put("RespondType", "JSON");
        form.put("CheckValue", checkValue);
        form.put("TimeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        form.put("MerchantOrderNo", status.getPaymentNo());
        form.put("Amt", toNewebPayAmount(status.getPaymentAmount()));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(newebPayProperties.getQueryUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(toQueryString(form)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> rawResponse = objectMapper.readValue(
                    response.body(),
                    new TypeReference<Map<String, Object>>() {
                    });
            syncPaidStatusFromQuery(status, rawResponse);
            return ApiResponse.success(
                    "NewebPay trade queried successfully",
                    new NewebPayQueryResponse(
                            status.getPaymentNo(),
                            status.getApplicationNo(),
                            newebPayProperties.getQueryUrl(),
                            rawResponse));
        } catch (Exception exception) {
            return ApiResponse.fail("NewebPay query failed");
        }
    }

    private void syncPaidStatusFromQuery(
            PaymentStatusResponse localStatus,
            Map<String, Object> rawResponse) {
        if (!isSuccessfulTradeQuery(rawResponse)) {
            return;
        }

        Map<String, Object> result = queryResult(rawResponse);
        String merchantOrderNo = stringValue(result.get("MerchantOrderNo"));
        if (!localStatus.getPaymentNo().equals(merchantOrderNo)) {
            throw new IllegalArgumentException("NewebPay query order number mismatch");
        }

        BigDecimal queryAmount = toAmount(result.get("Amt"));
        if (localStatus.getPaymentAmount() == null || localStatus.getPaymentAmount().compareTo(queryAmount) != 0) {
            throw new IllegalArgumentException("NewebPay query amount mismatch");
        }

        Map<String, Object> payment = paymentRepository.findPaymentWithApplication(localStatus.getPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        Long applicationId = toLong(payment.get("applicationId"));
        paymentRepository.markPaymentPaid(
                localStatus.getPaymentNo(),
                stringValue(result.get("TradeNo")),
                parsePayTime(stringValue(result.get("PayTime"))));
        int updatedApplications = paymentRepository.updateApplicationPaymentStatus(applicationId, "PAID");
        if (updatedApplications > 0) {
            notificationService.notifyPaymentStatusChanged(
                    toLong(payment.get("userId")),
                    applicationId,
                    stringValue(payment.get("eventTitle")),
                    true);
            notificationService.notifyOrganizerPaymentStatusChanged(
                    toLong(payment.get("organizerUserId")),
                    applicationId,
                    stringValue(payment.get("eventTitle")),
                    stringValue(payment.get("brandName")),
                    true);
        }
    }

    private boolean isSuccessfulTradeQuery(Map<String, Object> rawResponse) {
        Map<String, Object> result = queryResult(rawResponse);
        return "SUCCESS".equalsIgnoreCase(stringValue(rawResponse.get("Status")))
                && "1".equals(stringValue(result.get("TradeStatus")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryResult(Map<String, Object> rawResponse) {
        Object result = rawResponse == null ? null : rawResponse.get("Result");
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @Transactional
    public String handleNotify(Map<String, String> payload) {
        Map<String, String> result = parseAndVerifyCallback(payload);
        String paymentNo = result.get("MerchantOrderNo");
        if (paymentNo == null || paymentNo.isBlank()) {
            return "0|MerchantOrderNo required";
        }

        Map<String, Object> payment = paymentRepository.findPaymentWithApplication(paymentNo).orElse(null);
        if (payment == null) {
            return "0|Payment not found";
        }

        syncPaymentStatusFromCallback(payment, result);
        return "1|OK";
    }
    @Transactional
    public String buildReturnUrl(Map<String, String> payload, String frontendUrl) {
        try {
            Map<String, String> result = parseAndVerifyCallback(payload);
            String paymentNo = result.getOrDefault("MerchantOrderNo", "");
            String status = result.getOrDefault("Status", "");
            Map<String, Object> payment = paymentNo.isBlank()
                    ? Map.of()
                    : paymentRepository.findPaymentWithApplication(paymentNo).orElse(Map.of());
            if (!payment.isEmpty()) {
                syncPaymentStatusFromCallback(payment, result);
            }
            String applicationNo = stringValue(payment.get("applicationNo"));
            return trimTrailingSlash(frontendUrl)
                    + "/vendor/dash-board/application-record"
                    + "?applicationNo=" + urlEncode(applicationNo)
                    + "&paymentNo=" + urlEncode(paymentNo)
                    + "&merchantOrderNo=" + urlEncode(paymentNo)
                    + "&status=" + urlEncode(status);
        } catch (RuntimeException exception) {
            return trimTrailingSlash(frontendUrl)
                    + "/vendor/dash-board/application-record"
                    + "?paymentStatus=invalid";
        }
    }

    private void syncPaymentStatusFromCallback(Map<String, Object> payment, Map<String, String> result) {
        Long applicationId = toLong(payment.get("applicationId"));
        String paymentNo = stringValue(payment.get("paymentNo"));
        String providerTradeNo = result.get("TradeNo");
        String status = result.get("Status");
        String currentPaymentStatus = stringValue(payment.get("paymentRecordStatus"));
        if ("SUCCESS".equalsIgnoreCase(status)) {
            assertCallbackAmount(payment, result);
            paymentRepository.markPaymentPaid(paymentNo, providerTradeNo, parsePayTime(result.get("PayTime")));
            int updatedApplications = paymentRepository.updateApplicationPaymentStatus(applicationId, "PAID");
            if (updatedApplications > 0) {
                notificationService.notifyPaymentStatusChanged(
                        toLong(payment.get("userId")),
                        applicationId,
                        stringValue(payment.get("eventTitle")),
                        true);
                notificationService.notifyOrganizerPaymentStatusChanged(
                        toLong(payment.get("organizerUserId")),
                        applicationId,
                        stringValue(payment.get("eventTitle")),
                        stringValue(payment.get("brandName")),
                        true);
            }
        } else if (!"PAID".equals(currentPaymentStatus)) {
            paymentRepository.markPaymentFailed(paymentNo, providerTradeNo);
            int updatedApplications = paymentRepository.updateApplicationPaymentStatus(applicationId, "FAILED");
            if (updatedApplications > 0) {
                notificationService.notifyPaymentStatusChanged(
                        toLong(payment.get("userId")),
                        applicationId,
                        stringValue(payment.get("eventTitle")),
                        false);
                notificationService.notifyOrganizerPaymentStatusChanged(
                        toLong(payment.get("organizerUserId")),
                        applicationId,
                        stringValue(payment.get("eventTitle")),
                        stringValue(payment.get("brandName")),
                        false);
            }
        }
    }

    private Map<String, String> buildMpgTradeInfo(
            String paymentNo,
            BigDecimal amount,
            Map<String, Object> application) {
        Map<String, String> tradeInfo = new LinkedHashMap<>();
        tradeInfo.put("MerchantID", newebPayProperties.getMerchantId());
        tradeInfo.put("RespondType", "String");
        tradeInfo.put("TimeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        tradeInfo.put("Version", newebPayProperties.getVersion());
        tradeInfo.put("MerchantOrderNo", paymentNo);
        tradeInfo.put("Amt", toNewebPayAmount(amount));
        tradeInfo.put("ItemDesc", limitItemDesc(application.get("eventName")));
        tradeInfo.put("NotifyURL", newebPayProperties.getNotifyUrl());
        tradeInfo.put("ReturnURL", newebPayProperties.getReturnUrl());
        tradeInfo.put("ClientBackURL", newebPayProperties.getReturnUrl());
        tradeInfo.put("CREDIT", "1");
        tradeInfo.put("LangType", "zh-tw");
        return tradeInfo;
    }

    private Map<String, String> parseAndVerifyCallback(Map<String, String> payload) {
        String tradeInfo = payload == null ? null : normalizeHex(firstPresent(payload, "TradeInfo"));
        String tradeSha = payload == null ? null : normalizeHex(firstPresent(payload, "TradeSha"));
        if (tradeInfo == null || tradeInfo.isBlank()) {
            throw new IllegalArgumentException("TradeInfo is required");
        }
        if (tradeSha == null || tradeSha.isBlank()) {
            throw new IllegalArgumentException("TradeSha is required");
        }

        String expectedTradeSha = sha256Upper("HashKey=" + newebPayProperties.getHashKey()
                + "&" + tradeInfo
                + "&HashIV=" + newebPayProperties.getHashIv());
        if (!MessageDigest.isEqual(
                expectedTradeSha.getBytes(StandardCharsets.UTF_8),
                tradeSha.toUpperCase().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("TradeSha verification failed");
        }

        return parseQueryString(decrypt(tradeInfo));
    }

    private ApiResponse<Void> validateVendorToken(String token) {
        if (token == null || token.isBlank()) {
            return ApiResponse.fail(401, "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail(401, "Invalid or expired token");
        }
        if (!"VENDOR".equals(jwtService.getRole(token))) {
            return ApiResponse.fail("This account is not a vendor");
        }
        return null;
    }

    private PaymentStatusResponse toPaymentStatusResponse(Map<String, Object> payment) {
        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setApplicationId(toLong(payment.get("applicationId")));
        response.setApplicationNo(stringValue(payment.get("applicationNo")));
        response.setReviewStatus(stringValue(payment.get("reviewStatus")));
        response.setApplicationPaymentStatus(stringValue(payment.get("applicationPaymentStatus")));
        response.setCancelled(isTrue(payment.get("isCancelled")));
        response.setApplicationAmount(toAmountOrNull(payment.get("applicationAmount")));
        response.setPaymentDueAt(toLocalDateTime(payment.get("paymentDueAt")));
        response.setPaymentId(toLong(payment.get("paymentId")));
        response.setPaymentNo(blankToNull(stringValue(payment.get("paymentNo"))));
        response.setMerchantOrderNo(response.getPaymentNo());
        response.setPaymentAmount(toAmountOrNull(payment.get("paymentAmount")));
        response.setProvider(blankToNull(stringValue(payment.get("provider"))));
        response.setProviderTradeNo(blankToNull(stringValue(payment.get("providerTradeNo"))));
        response.setPaymentRecordStatus(blankToNull(stringValue(payment.get("paymentRecordStatus"))));
        response.setPaidAt(toLocalDateTime(payment.get("paidAt")));
        response.setPaymentCreatedAt(toLocalDateTime(payment.get("paymentCreatedAt")));
        return response;
    }

    private String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(newebPayProperties.getHashKey().getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(newebPayProperties.getHashIv().getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("NewebPay TradeInfo encryption failed", exception);
        }
    }

    private String decrypt(String encryptedHex) {
        String normalizedHex = normalizeHex(encryptedHex);
        if (normalizedHex == null || normalizedHex.isBlank()) {
            throw new IllegalArgumentException("NewebPay TradeInfo decryption failed: TradeInfo is blank");
        }
        if (normalizedHex.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "NewebPay TradeInfo decryption failed: hex length is odd (" + normalizedHex.length() + ")");
        }

        byte[] encryptedBytes;
        try {
            encryptedBytes = HexFormat.of().parseHex(normalizedHex);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "NewebPay TradeInfo decryption failed: TradeInfo is not valid hex", exception);
        }
        if (encryptedBytes.length == 0 || encryptedBytes.length % 16 != 0) {
            throw new IllegalArgumentException(
                    "NewebPay TradeInfo decryption failed: cipher byte length is not AES block aligned ("
                            + encryptedBytes.length + ")");
        }

        Exception noPaddingException = null;
        try {
            return decryptNoPadding(encryptedBytes);
        } catch (Exception exception) {
            noPaddingException = exception;
        }

        try {
            return decryptPkcs5Padding(encryptedBytes);
        } catch (Exception pkcs5Exception) {
            throw new IllegalArgumentException(
                    "NewebPay TradeInfo decryption failed: "
                            + "NoPadding="
                            + describeException(noPaddingException)
                            + "; PKCS5Padding="
                            + describeException(pkcs5Exception),
                    pkcs5Exception);
        }
    }

    private String decryptNoPadding(byte[] encryptedBytes) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(newebPayProperties.getHashKey().getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(newebPayProperties.getHashIv().getBytes(StandardCharsets.UTF_8)));
        byte[] decrypted = cipher.doFinal(encryptedBytes);
        return new String(stripPkcs7Padding(decrypted), StandardCharsets.UTF_8);
    }

    private String decryptPkcs5Padding(byte[] encryptedBytes) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(newebPayProperties.getHashKey().getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(newebPayProperties.getHashIv().getBytes(StandardCharsets.UTF_8)));
        return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
    }

    private byte[] stripPkcs7Padding(byte[] value) {
        if (value.length == 0) {
            throw new IllegalArgumentException("Empty decrypted TradeInfo");
        }

        int padding = value[value.length - 1] & 0xff;
        if (padding < 1 || padding > 32 || padding > value.length) {
            throw new IllegalArgumentException(
                    "Invalid TradeInfo padding: padding=" + padding + ", decryptedLength=" + value.length);
        }

        for (int index = value.length - padding; index < value.length; index++) {
            if ((value[index] & 0xff) != padding) {
                throw new IllegalArgumentException(
                        "Invalid TradeInfo padding: mismatch at byte " + index + ", padding=" + padding);
            }
        }

        byte[] unpadded = new byte[value.length - padding];
        System.arraycopy(value, 0, unpadded, 0, unpadded.length);
        return unpadded;
    }

    private String sha256Upper(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).toUpperCase();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA256 generation failed", exception);
        }
    }

    private String toQueryString(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private Map<String, String> parseQueryString(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String pair : value.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                result.put(urlDecode(pair), "");
                continue;
            }
            result.put(
                    urlDecode(pair.substring(0, separator)),
                    urlDecode(pair.substring(separator + 1)));
        }
        return result;
    }

    private String generatePaymentNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        return "PAY" + LocalDateTime.now().format(PAYMENT_NO_TIME_FORMAT) + suffix;
    }

    private String toNewebPayAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private boolean isValidNewebPayAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        try {
            amount.setScale(0, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private void assertCallbackAmount(Map<String, Object> payment, Map<String, String> result) {
        BigDecimal expectedAmount = toAmount(payment.get("amount"));
        BigDecimal callbackAmount = new BigDecimal(result.getOrDefault("Amt", "0"));
        if (expectedAmount.compareTo(callbackAmount) != 0) {
            throw new IllegalArgumentException("Payment amount mismatch");
        }
    }

    private BigDecimal toAmount(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private BigDecimal toAmountOrNull(Object value) {
        return value == null ? null : toAmount(value);
    }

    private LocalDateTime parsePayTime(String payTime) {
        if (payTime == null || payTime.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(payTime.trim(), PAY_TIME_FORMAT);
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

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.valueOf(string);
        }
        return null;
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String limitItemDesc(Object eventName) {
        String value = stringValue(eventName);
        if (value.isBlank()) {
            value = "MarketDay";
        }
        value = value.replaceAll("[\\r\\n'\"&=]", " ").trim();
        return value.length() > 50 ? value.substring(0, 50) : value;
    }

    private boolean isNewebPayConfigComplete() {
        return !isBlank(newebPayProperties.getMerchantId())
                && !isBlank(newebPayProperties.getHashKey())
                && newebPayProperties.getHashKey().length() == 32
                && !isBlank(newebPayProperties.getHashIv())
                && newebPayProperties.getHashIv().length() == 16
                && !isBlank(newebPayProperties.getVersion())
                && !isBlank(newebPayProperties.getGateway())
                && !isBlank(newebPayProperties.getNotifyUrl())
                && !isBlank(newebPayProperties.getReturnUrl());
    }

    private boolean isNewebPayQueryConfigComplete() {
        return isNewebPayConfigComplete() && !isBlank(newebPayProperties.getQueryUrl());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String firstPresent(Map<String, String> payload, String key) {
        String exact = payload.get(key);
        if (exact != null) {
            return exact;
        }
        return payload.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String normalizeHex(String value) {
        return value == null ? null : value.replaceAll("\\s+", "").trim();
    }

    private String describeException(Exception exception) {
        if (exception == null) {
            return "unknown";
        }
        String message = blankToNull(exception.getMessage());
        return exception.getClass().getSimpleName() + (message == null ? "" : "(" + message + ")");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
