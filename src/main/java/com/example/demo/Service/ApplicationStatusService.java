package com.example.demo.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * (狀態)轉換成前端需要的格式
 * ApplicationStatusService
 */
@Service
public class ApplicationStatusService {

    /**
     * (狀態)轉換成前端需要的格式
     * 
     * @param application
     * @return
     */
    public String resolveApplicationStatus(Map<String, Object> application) {
        if (isTrue(application.get("isCancelled"))) {
            return "已取消";
        }

        String refundStatus = stringValue(application.get("refundStatus"));
        if ("REFUNDED".equals(refundStatus)) {
            return "已退款";
        }
        if ("REFUNDING".equals(refundStatus) || "REFUND_FAILED".equals(refundStatus)) {
            return "退款處理中";
        }
        if ("REFUND_REQUESTED".equals(refundStatus)) {
            return "退款申請中";
        }

        String reviewStatus = stringValue(application.get("reviewStatus"));
        if ("PENDING".equals(reviewStatus)) {
            return "待審核";
        }
        if ("REJECTED".equals(reviewStatus)) {
            return "審核未通過";
        }

        String paymentStatus = stringValue(application.get("paymentStatus"));
        if ("EXPIRED".equals(paymentStatus)) {
            return "已取消";
        }
        if ("PENDING".equals(paymentStatus) || "FAILED".equals(paymentStatus)) {
            return "待付款";
        }

        if ("PAID".equals(paymentStatus) && !isAllApplicationDatesSelected(application)) {
            return "待選位";
        }

        if ("PAID".equals(paymentStatus)) {
            if (isEventEnded(application.get("eventEndAt"))
                    && "RETURNED".equals(stringValue(application.get("depositStatus")))) {
                return "保證金已退還";
            }
            return "報名完成";
        }

        return "待審核";
    }

    private boolean isEventEnded(Object value) {
        LocalDate eventEndDay = toLocalDate(value);
        return eventEndDay != null && !eventEndDay.isAfter(LocalDate.now());
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = value == null ? "" : value.toString().trim();
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() == 1;
        }
        return "true".equalsIgnoreCase(stringValue(value)) || "1".equals(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim().toUpperCase();
    }

    private boolean isAllApplicationDatesSelected(Map<String, Object> application) {
        Long applicationDateCount = toLong(application.get("applicationDateCount"));
        Long selectedStallCount = toLong(application.get("selectedStallCount"));
        if (applicationDateCount > 0) {
            return applicationDateCount.equals(selectedStallCount);
        }
        return application.get("selectedStallId") != null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.valueOf(string);
        }
        return 0L;
    }
}
