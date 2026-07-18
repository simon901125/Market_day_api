package com.example.demo.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.PaymentRepository;
import com.example.demo.dto.request.VendorRefundRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.VendorRefundResponse;

@Service
public class VendorRefundService {

    private static final DateTimeFormatter REFUND_NO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String REFUND_STATUS_REQUESTED = "REFUND_REQUESTED";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public ApiResponse<VendorRefundResponse> requestRefund(
            String authorizationHeader,
            VendorRefundRequest body) {
        ApiResponse<Void> validationError = validateRequest(body);
        if (validationError != null) {
            return ApiResponse.fail(validationError.getStatusCode(), validationError.getMessage());
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        ApiResponse<Void> authError = validateVendorToken(token);
        if (authError != null) {
            return ApiResponse.fail(authError.getStatusCode(), authError.getMessage());
        }

        Map<String, Object> vendor = paymentRepository.findVendorPaymentUserByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (vendor == null) {
            return ApiResponse.fail("Vendor profile not found");
        }

        String applicationNo = body.getApplicationNo().trim();
        String reason = body.getReason().trim();
        Map<String, Object> application = paymentRepository.findRefundableApplication(applicationNo).orElse(null);
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
        if (!"PAID".equals(stringValue(application.get("paymentStatus")))) {
            return ApiResponse.fail("Application payment is not paid");
        }

        Long applicationId = toLong(application.get("applicationId"));
        Map<String, Object> paidPayment = paymentRepository.findLatestPaidPayment(applicationId).orElse(null);
        if (paidPayment == null) {
            return ApiResponse.fail("Payment record not found");
        }
        if (paymentRepository.findLatestRefundByApplicationId(applicationId).isPresent()) {
            return ApiResponse.fail("此報名已提出退款申請");
        }

        BigDecimal paymentAmount = toAmount(paidPayment.get("amount"));
        BigDecimal depositAmount = toAmount(application.get("depositAmount"));
        BigDecimal refundAmount = paymentAmount.subtract(depositAmount).setScale(0, RoundingMode.DOWN);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.fail("可退款金額不正確");
        }

        String refundNo = generateRefundNo();
        Long paymentId = toLong(paidPayment.get("paymentId"));
        Long refundId = paymentRepository.createRefund(
                refundNo,
                applicationId,
                paymentId,
                refundAmount,
                reason);
        if (refundId == null) {
            return ApiResponse.fail("退款申請送出失敗");
        }

        notifyOrganizer(application, refundId);

        String paymentNo = stringValue(paidPayment.get("paymentNo"));
        VendorRefundResponse response = new VendorRefundResponse(
                refundId,
                refundNo,
                applicationId,
                stringValue(application.get("applicationNo")),
                paymentId,
                paymentNo,
                paymentNo,
                stringValue(paidPayment.get("providerTradeNo")),
                refundAmount,
                depositAmount,
                refundMethod(paidPayment.get("provider")),
                REFUND_STATUS_REQUESTED,
                reason,
                LocalDateTime.now());

        return ApiResponse.success("退款申請送出成功", response);
    }

    private ApiResponse<Void> validateRequest(VendorRefundRequest body) {
        if (body == null || body.getApplicationNo() == null || body.getApplicationNo().isBlank()) {
            return ApiResponse.fail("Application number is required");
        }
        if (body.getReason() == null || body.getReason().isBlank()) {
            return ApiResponse.fail("請輸入退款原因");
        }
        if (body.getReason().trim().length() > 255) {
            return ApiResponse.fail("退款原因不得超過 255 個字");
        }
        return null;
    }

    private ApiResponse<Void> validateVendorToken(String token) {
        if (token == null || token.isBlank()) {
            return ApiResponse.fail(401, "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail(401, "Invalid or expired token");
        }
        if (!"VENDOR".equals(jwtService.getRole(token))) {
            return ApiResponse.fail(403, "This account is not a vendor");
        }
        return null;
    }

    private void notifyOrganizer(Map<String, Object> application, Long refundId) {
        Long organizerUserId = toLong(application.get("organizerUserId"));
        if (organizerUserId == null || refundId == null) {
            return;
        }
        notificationService.notifyRefundRequested(
                organizerUserId,
                refundId,
                stringValue(application.get("eventName")));
    }

    private String generateRefundNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        return "REF" + LocalDateTime.now().format(REFUND_NO_TIME_FORMAT) + suffix;
    }

    private String refundMethod(Object provider) {
        String value = stringValue(provider);
        return value.isBlank() ? "NEWEBPAY" : value;
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(stringValue(value));
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

    private BigDecimal toAmount(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            return new BigDecimal(string);
        }
        return BigDecimal.ZERO;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        return value.toString().trim();
    }
}
