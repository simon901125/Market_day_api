package com.example.demo.enums.notification;

public enum VendorNotificationFilter {
    ALL(null, false),
    UNREAD(null, true),
    APPLICATION_REVIEW(NotificationCategory.APPLICATION_REVIEW, false),
    PAYMENT(NotificationCategory.PAYMENT, false),
    STALL_ASSIGNMENT(NotificationCategory.STALL_ASSIGNMENT, false),
    EVENT_CHANGE(NotificationCategory.EVENT_CHANGE, false);

    private final NotificationCategory category;
    private final boolean unreadOnly;

    VendorNotificationFilter(NotificationCategory category, boolean unreadOnly) {
        this.category = category;
        this.unreadOnly = unreadOnly;
    }

    public NotificationCategory category() {
        return category;
    }

    public boolean unreadOnly() {
        return unreadOnly;
    }

    public static VendorNotificationFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim()) {
            case "全部" -> ALL;
            case "未讀" -> UNREAD;
            case "報名審核" -> APPLICATION_REVIEW;
            case "付款相關" -> PAYMENT;
            case "攤位分配" -> STALL_ASSIGNMENT;
            case "活動異動" -> EVENT_CHANGE;
            default -> throw new IllegalArgumentException("Vendor notification filter is invalid");
        };
    }
}
