package com.example.demo.enums.notification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 通知事件類型，例如 APPLICATION_APPROVED/PAYMENT_PAID/EVENT_UPDATED（對應API實際操作行為分類）
 *
 * @see com.example.demo.entity.Notification
 */
@Getter
@RequiredArgsConstructor
public enum NotificationType {

    /**主辦方資格申請已送出 */
    ORGANIZER_REGISTRATION_SUBMITTED("organizerRegistrationSubmitted", "主辦方申請已送出"),

    /**主辦方資料已重新送出審核 */
    ORGANIZER_PROFILE_RESUBMITTED("organizerProfileResubmitted", "主辦方資料已重新送出"),

    /**活動已送出審核 */
    EVENT_SUBMITTED("eventSubmitted", "活動已送出審核"),

    /**活動已重新送出審核 */
    EVENT_RESUBMITTED("eventResubmitted", "活動已重新送出審核"),

    /**活動審核通過，進入地圖建置 */
    EVENT_APPROVED("eventApproved", "活動審核通過"),

    /**活動審核未通過，需補件 */
    EVENT_REVISION_REQUIRED("eventRevisionRequired", "活動需補件"),

    /**活動攤位地圖建置完成 */
    EVENT_MAP_COMPLETED("eventMapCompleted", "活動地圖建置完成"),

    /**攤位申請已送出 */
    APPLICATION_SUBMITTED("applicationSubmitted", "攤位申請已送出"),

    /**攤位申請已重新送出 */
    APPLICATION_RESUBMITTED("applicationResubmitted", "攤位申請已重新送出"),

    /**攤位申請已通過 */
    APPLICATION_APPROVED("applicationApproved", "攤位申請已通過"),

    /**攤位申請已拒絕 */
    APPLICATION_REJECTED("applicationRejected", "攤位申請已拒絕"),

    /**攤位申請已取消 */
    APPLICATION_CANCELLED("applicationCancelled", "攤位申請已取消"),

    /**付款成功 */
    PAYMENT_PAID("paymentPaid", "付款成功"),

    /**付款失敗 */
    PAYMENT_FAILED("paymentFailed", "付款失敗"),

    /**付款逾期 */
    PAYMENT_EXPIRED("paymentExpired", "付款逾期"),

    /**可開放選擇攤位 */
    STALL_SELECTION_AVAILABLE("stallSelectionAvailable", "可選擇攤位"),

    /**攤位選擇已完成 */
    STALL_SELECTION_COMPLETED("stallSelectionCompleted", "攤位選擇完成"),

    /**報名流程已完成 */
    APPLICATION_COMPLETED("applicationCompleted", "報名完成"),

    /**活動內容已更新 */
    EVENT_UPDATED("eventUpdated", "活動已更新"),

    /**活動已下架 */
    EVENT_UNPUBLISHED("eventUnpublished", "活動已下架"),

    /**活動已取消 */
    EVENT_CANCELLED("eventCancelled", "活動已取消"),

    /**活動已結束 */
    EVENT_ENDED("eventEnded", "活動已結束"),

    /**活動下架申請退回，需補件 */
    EVENT_UNPUBLISH_REQUEST_REVISION_REQUIRED("eventUnpublishRequestRevisionRequired", "活動下架申請需補件"),

    /**退款已申請 */
    REFUND_REQUESTED("refundRequested", "退款申請已送出"),

    /**退款處理中 */
    REFUNDING("refunding", "退款處理中"),

    /**退款失敗 */
    REFUND_FAILED("refundFailed", "退款失敗"),

    /**退款已完成 */
    REFUNDED("refunded", "退款完成"),

    /**保證金已由主辦方登記為現金退還 */
    DEPOSIT_RETURNED("depositReturned", "保證金已退還"),

    /**系統公告 */
    SYSTEM_ANNOUNCEMENT("systemAnnouncement", "系統公告"),

    /**系統例外事件通知 */
    SYSTEM_EXCEPTION("systemException", "系統例外通知"),

    /**登入異常 */
    LOGIN_ANOMALY("loginAnomaly", "登入異常"),

    /**密碼重設完成 */
    PASSWORD_RESET_COMPLETED("passwordResetCompleted", "密碼重設完成");

    @JsonValue
    private final String type; //序列化
    private final String description;

    @JsonCreator //反序列化
    public static NotificationType fromType(String type) {
        for (NotificationType t : values()) {
            if (t.type.equalsIgnoreCase(type) || t.description.equals(type) || t.name().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
