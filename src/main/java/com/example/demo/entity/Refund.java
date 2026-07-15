package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

import com.example.demo.enums.status.RefundStatus;

/**
 * 退款紀錄的Entity。<br>
 * 包含退款單編號、金額、退款原因、退款失敗原因、退款狀態{@link RefundStatus}、退款完成時間<br>
 * <b>FK</b>: 攤位報名{@link EventApplication#refunds}、付款紀錄{@link Payment#refunds}
 */
@Entity
@Data
@Table(name = "refunds", uniqueConstraints = @UniqueConstraint(name = "UQ_refunds_refund_no", columnNames = "refund_no"))
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**退款單編號 */
    @Column(name = "refund_no", length = 40, nullable = false, unique = true)
    private String refundNo;

    /**所屬的攤位報名 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, foreignKey = @ForeignKey(name = "FK_refunds_event_applications"))
    private EventApplication application;

    /**對應的付款紀錄 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", foreignKey = @ForeignKey(name = "FK_refunds_payments"))
    private Payment payment;

    /**金額 */
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    /**退款原因 */
    @Column(name = "reason", length = 255)
    private String reason;

    /**退款失敗原因 */
    @Column(name = "failed_reason", length = 255)
    private String failedReason;

    /**退款狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 30, nullable = false)
    private RefundStatus refundStatus = RefundStatus.REFUND_REQUESTED;

    /**退款完成時間 */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

}
