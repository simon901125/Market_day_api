package com.example.demo.Service;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PaymentStatusService {

    public String resolvePaymentStatus(Map<String, Object> application) {
        String refundStatus = stringValue(application.get("refundStatus"));
        if ("REFUNDED".equals(refundStatus)) {
            return "已退款";
        }
        if ("REFUND_FAILED".equals(refundStatus)) {
            return "退款失敗";
        }
        if ("REFUNDING".equals(refundStatus)) {
            return "退款處理中";
        }
        if ("REFUND_REQUESTED".equals(refundStatus)) {
            return "已申請退款";
        }

        return switch (stringValue(application.get("paymentStatus"))) {
            case "PAID" -> "已付款";
            case "FAILED" -> "付款失敗";
            case "EXPIRED" -> "已逾期";
            case "PENDING" -> "待付款";
            default -> null;
        };
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim().toUpperCase();
    }
}
