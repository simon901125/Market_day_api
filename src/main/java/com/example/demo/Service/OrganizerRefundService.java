package com.example.demo.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.PaymentRepository;
import com.example.demo.dto.request.OrganizerRefundRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.NewebPayRefundResultResponse;
import com.example.demo.dto.response.OrganizerRefundResponse;

@Service
public class OrganizerRefundService {

    private static final String REFUND_STATUS_REQUESTED = "REFUND_REQUESTED";
    private static final String REFUND_STATUS_REFUNDING = "REFUNDING";
    private static final String REFUND_STATUS_FAILED = "REFUND_FAILED";
    private static final String REFUND_STATUS_REFUNDED = "REFUNDED";
    private static final Set<String> PAYMENT_RETRY_STATUSES = Set.of(REFUND_STATUS_REFUNDING, REFUND_STATUS_FAILED);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NewebPayService newebPayService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public ApiResponse<OrganizerRefundResponse> reviewRefund(
            String authorizationHeader,
            OrganizerRefundRequest body) {
        return processRefund(authorizationHeader, body, Set.of(REFUND_STATUS_REQUESTED), "Organizer refund reviewed successfully");
    }

    @Transactional
    public ApiResponse<OrganizerRefundResponse> retryRefundPayment(
            String authorizationHeader,
            OrganizerRefundRequest body) {
        return processRefund(authorizationHeader, body, PAYMENT_RETRY_STATUSES, "Organizer refund payment retried successfully");
    }

    private ApiResponse<OrganizerRefundResponse> processRefund(
            String authorizationHeader,
            OrganizerRefundRequest body,
            Set<String> allowedStatuses,
            String successMessage) {
        ApiResponse<Void> validationError = validateRequest(body);
        if (validationError != null) {
            return ApiResponse.fail(validationError.getStatusCode(), validationError.getMessage());
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        ApiResponse<Void> authError = validateOrganizerToken(token);
        if (authError != null) {
            return ApiResponse.fail(authError.getStatusCode(), authError.getMessage());
        }

        Map<String, Object> organizer = paymentRepository.findOrganizerPaymentUserByEmail(jwtService.getEmail(token))
                .orElse(null);
        if (organizer == null) {
            return ApiResponse.fail("Organizer profile not found");
        }

        String refundNo = body.getRefundNo().trim();
        Map<String, Object> refund = paymentRepository.findRefundForOrganizerProcessing(refundNo).orElse(null);
        if (refund == null) {
            return ApiResponse.fail("Refund record not found");
        }

        Long organizerUserId = toLong(organizer.get("userId"));
        Long refundOrganizerUserId = toLong(refund.get("organizerUserId"));
        if (organizerUserId == null || !organizerUserId.equals(refundOrganizerUserId)) {
            return ApiResponse.fail("Refund record does not belong to this organizer");
        }

        String refundStatus = stringValue(refund.get("refundStatus"));
        if (!allowedStatuses.contains(refundStatus)) {
            return new ApiResponse<>(400, refundStatusNotAllowedMessage(refundStatus, allowedStatuses), null);
        }
        ApiResponse<Void> paymentError = validatePaidNewebPayPayment(refund);
        if (paymentError != null) {
            return ApiResponse.fail(paymentError.getStatusCode(), paymentError.getMessage());
        }

        Long refundId = toLong(refund.get("refundId"));
        paymentRepository.markRefundProcessing(refundId);

        try {
            NewebPayRefundResultResponse newebpayResult = newebPayService.closeCreditCardRefund(
                    stringValue(refund.get("paymentNo")),
                    stringValue(refund.get("providerTradeNo")),
                    toAmount(refund.get("refundAmount")),
                    toAmount(refund.get("paymentAmount")));
            paymentRepository.markRefundSucceeded(refundId);
            Map<String, Object> updatedRefund = paymentRepository.findRefundForOrganizerProcessing(refundNo)
                    .orElse(refund);
            OrganizerRefundResponse response = toResponse(updatedRefund, newebpayResult);
            notifyRefundSucceeded(updatedRefund);
            return ApiResponse.success(successMessage, response);
        } catch (RuntimeException exception) {
            String failedReason = truncateFailureReason(exception.getMessage());
            paymentRepository.markRefundFailed(refundId, failedReason);
            Map<String, Object> updatedRefund = paymentRepository.findRefundForOrganizerProcessing(refundNo)
                    .orElse(refund);
            OrganizerRefundResponse response = toResponse(updatedRefund, null);
            notifyRefundFailed(updatedRefund);
            return new ApiResponse<>(502, "NewebPay refund failed", failedReason, response);
        }
    }

    private ApiResponse<Void> validateRequest(OrganizerRefundRequest body) {
        if (body == null || body.getRefundNo() == null || body.getRefundNo().isBlank()) {
            return ApiResponse.fail("Refund number is required");
        }
        return null;
    }

    private ApiResponse<Void> validateOrganizerToken(String token) {
        if (token == null || token.isBlank()) {
            return ApiResponse.fail(401, "Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail(401, "Invalid or expired token");
        }
        if (!"ORGANIZER".equals(jwtService.getRole(token))) {
            return ApiResponse.fail(403, "This account is not an organizer");
        }
        return null;
    }

    private String refundStatusNotAllowedMessage(String refundStatus, Set<String> allowedStatuses) {
        boolean reviewAction = allowedStatuses.size() == 1 && allowedStatuses.contains(REFUND_STATUS_REQUESTED);
        if (reviewAction) {
            return switch (refundStatus) {
                case REFUND_STATUS_REFUNDING -> "\u9000\u6B3E\u5DF2\u5728\u8655\u7406\u4E2D\uFF0C\u8ACB\u52FF\u91CD\u8907\u78BA\u8A8D";
                case REFUND_STATUS_FAILED -> "\u9000\u6B3E\u66FE\u57F7\u884C\u5931\u6557\uFF0C\u8ACB\u4F7F\u7528\u9000\u6B3E\u91D1\u6D41\u91CD\u8A66 API";
                case REFUND_STATUS_REFUNDED -> "\u6B64\u9000\u6B3E\u5DF2\u5B8C\u6210\uFF0C\u4E0D\u53EF\u91CD\u8907\u9000\u6B3E";
                default -> "\u6B64\u9000\u6B3E\u72C0\u614B\u4E0D\u53EF\u78BA\u8A8D\u9000\u6B3E";
            };
        }
        return "\u6B64\u9000\u6B3E\u72C0\u614B\u4E0D\u53EF\u57F7\u884C\u9000\u6B3E\u91D1\u6D41\u91CD\u8A66";
    }

    private ApiResponse<Void> validatePaidNewebPayPayment(Map<String, Object> refund) {
        if (!"PAID".equals(stringValue(refund.get("paymentStatus")))) {
            return ApiResponse.fail("Payment record is not paid");
        }
        if (!"NEWEBPAY".equals(stringValue(refund.get("provider")))) {
            return ApiResponse.fail("Refund payment provider is not NewebPay");
        }
        if (stringValue(refund.get("paymentNo")).isBlank()) {
            return ApiResponse.fail("Payment number is required");
        }
        if (stringValue(refund.get("providerTradeNo")).isBlank()) {
            return ApiResponse.fail("NewebPay trade number is required");
        }
        return null;
    }

    private OrganizerRefundResponse toResponse(
            Map<String, Object> refund,
            NewebPayRefundResultResponse newebpayResult) {
        String paymentNo = stringValue(refund.get("paymentNo"));
        return new OrganizerRefundResponse(
                toLong(refund.get("refundId")),
                stringValue(refund.get("refundNo")),
                toAmount(refund.get("refundAmount")),
                stringValue(refund.get("refundStatus")),
                toLocalDateTime(refund.get("refundedAt")),
                toLong(refund.get("applicationId")),
                stringValue(refund.get("applicationNo")),
                toLong(refund.get("paymentId")),
                paymentNo,
                paymentNo,
                stringValue(refund.get("providerTradeNo")),
                newebpayResult);
    }

    private void notifyRefundSucceeded(Map<String, Object> refund) {
        Long refundId = toLong(refund.get("refundId"));
        String eventName = stringValue(refund.get("eventName"));
        notificationService.notifyRefundSucceededToVendor(toLong(refund.get("vendorUserId")), refundId, eventName);
        notificationService.notifyRefundSucceededToOrganizer(toLong(refund.get("organizerUserId")), refundId, eventName);
    }

    private void notifyRefundFailed(Map<String, Object> refund) {
        Long refundId = toLong(refund.get("refundId"));
        notificationService.notifyRefundFailedToOrganizer(
                toLong(refund.get("organizerUserId")),
                refundId,
                stringValue(refund.get("eventName")));
    }

    private String truncateFailureReason(String value) {
        String reason = value == null || value.isBlank() ? "NewebPay refund failed" : value.trim();
        return reason.length() > 255 ? reason.substring(0, 255) : reason;
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

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}

