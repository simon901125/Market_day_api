package com.example.demo.enums.notification;

import java.util.Set;

public enum OrganizerNotificationFilter {
    ALL(Set.of(), false),
    UNREAD(Set.of(), true),
    REGISTRATION(Set.of(
            NotificationCategory.APPLICATION_REVIEW,
            NotificationCategory.REGISTRATION,
            NotificationCategory.STALL_ASSIGNMENT), false),
    PAYMENT(Set.of(NotificationCategory.PAYMENT), false),
    EVENT_CHANGE(Set.of(
            NotificationCategory.EVENT_CHANGE,
            NotificationCategory.EVENT_MANAGEMENT), false),
    SYSTEM(Set.of(
            NotificationCategory.SYSTEM,
            NotificationCategory.EXCEPTION), false);

    private final Set<NotificationCategory> categories;
    private final boolean unreadOnly;

    OrganizerNotificationFilter(Set<NotificationCategory> categories, boolean unreadOnly) {
        this.categories = categories;
        this.unreadOnly = unreadOnly;
    }

    public Set<NotificationCategory> categories() {
        return categories;
    }

    public boolean unreadOnly() {
        return unreadOnly;
    }

    public static OrganizerNotificationFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.trim()) {
            case "全部" -> ALL;
            case "未讀" -> UNREAD;
            case "報名相關" -> REGISTRATION;
            case "付款相關" -> PAYMENT;
            case "活動異動" -> EVENT_CHANGE;
            case "系統公告" -> SYSTEM;
            default -> throw new IllegalArgumentException("Organizer notification filter is invalid");
        };
    }
}
