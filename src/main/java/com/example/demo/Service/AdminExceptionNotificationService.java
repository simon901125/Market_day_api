package com.example.demo.Service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.NotificationRepo;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;

@Service
public class AdminExceptionNotificationService {

    private final NotificationRepo notificationRepo;

    public AdminExceptionNotificationService(NotificationRepo notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyMissingUnpublishRequest(User admin, Long eventId, String eventTitle) {
        if (admin == null || eventId == null || eventId <= 0 || eventTitle == null || eventTitle.isBlank()) {
            throw new IllegalArgumentException("Admin, event id and event title are required for exception notification");
        }
        Notification notification = new Notification();
        notification.setUser(admin);
        notification.setCategory(NotificationCategory.EXCEPTION);
        notification.setType(NotificationType.SYSTEM_EXCEPTION);
        notification.setTargetType(NotificationTargetType.MARKET_EVENT);
        notification.setTargetId(eventId);
        notification.setTitle("活動狀態異常");
        notification.setContent("活動「" + eventTitle.trim() + "」（活動 ID：" + eventId
                + "）目前為申請下架狀態，但資料庫查無對應的活動下架申請單，請立即確認資料一致性");
        notificationRepo.save(notification);
    }
}
