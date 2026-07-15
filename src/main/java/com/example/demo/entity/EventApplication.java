package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import com.example.demo.enums.status.DepositStatus;
import com.example.demo.enums.status.PaymentStatus;
import com.example.demo.enums.status.ReviewStatus;

/**
 * 攤位報名的Entity。<br>
 * 包含報名單編號、車牌號碼、申請人備註、建立時間、是否已取消<br>
 * <b>金流相關</b>: 總金額、付款狀態{@link PaymentStatus}、保證金金額、保證金狀態{@link DepositStatus}、付款截止時間<br>
 * <b>審核相關</b>: 審核狀態{@link }、審核備註<br>
 * <b>FK</b>: 市集活動{@link MarketEvent#eventApplications}、使用者{@link User#eventApplications}、攤主資料(尚未實作)
 * 
 * 
 */
@Entity
@Data
@Table(name = "event_applications", uniqueConstraints = {
        @UniqueConstraint(name = "UQ_event_applications_application_no", columnNames = "application_no"),
        @UniqueConstraint(name = "UQ_event_applications_event_vendor_profile", columnNames = { "event_id",
                "vendor_profile_id" })
})
public class EventApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**報名的活動 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "FK_event_applications_market_events"))
    private MarketEvent event;

    /**報名的使用者 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_event_applications_users"))
    private User user;
    
    //TODO:建立攤主資料Entity後要連上去
    /**報名的攤主資料id */
    @Column(name = "vendor_profile_id", nullable = false)
    private Long vendorProfileId;
    
    /**報名單編號 */
    @Column(name = "application_no", length = 30, nullable = false)
    private String applicationNo;

    /**車牌號碼 */
    @Column(name = "vehicle_no", length = 30)
    private String vehicleNo;

    /**申請人備註 */
    @Column(name = "applicant_note", columnDefinition = "nvarchar(max)")
    private String applicantNote;

    /**總金額 */
    @Column(name = "total_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    /**保證金金額 */
    @Column(name = "deposit_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /**保證金狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_status", length = 30, nullable = false)
    private DepositStatus depositStatus = DepositStatus.NOT_RETURNED;

    /**付款截止時間 */
    @Column(name = "payment_due_at")
    private LocalDateTime paymentDueAt;

    /**審核狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 30, nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    /**付款狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30, nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /**是否已取消 */
    @Column(name = "is_cancelled", nullable = false)
    private Boolean isCancelled = false;

    /**建立時間 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
