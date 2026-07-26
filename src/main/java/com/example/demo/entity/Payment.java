package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import com.example.demo.enums.status.PaymentStatus;

/**
 * 付款紀錄的Entity。<br>
 * 包含付款單編號、金額、金流服務商、金流服務商交易編號、金流服務商回應代碼、金流服務商回應訊息、付款狀態{@link PaymentStatus}、付款完成時間、建立時間<br>
 * <b>FK</b>: 攤位報名{@link EventApplication#payments}
 *
 * @see Refund#payment
 */
@Entity
@Data
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "UQ_payments_payment_no", columnNames = "payment_no"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**付款單編號 */
    @Column(name = "payment_no", length = 40, nullable = false, unique = true)
    private String paymentNo;

    /**所屬的攤位報名 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, foreignKey = @ForeignKey(name = "FK_payments_event_applications"))
    private EventApplication application;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "payment_account_id",
            foreignKey = @ForeignKey(name = "FK_payments_payment_account"))
    private OrganizerPaymentAccount paymentAccount;

    /**金額 */
    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    /**金流服務商 */
    @Column(name = "provider", length = 30)
    private String provider;

    /**金流服務商交易編號 */
    @Column(name = "provider_trade_no", length = 100)
    private String providerTradeNo;

    /**金流服務商回應代碼 */
    @Column(name = "provider_response_code", length = 20)
    private String providerResponseCode;

    /**金流服務商回應訊息 */
    @Column(name = "provider_message", length = 255)
    private String providerMessage;

    /**付款狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PaymentStatus status;

    /**付款完成時間 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    /**建立時間 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ----------其他Entity的FK----------

    /**此付款的退款紀錄清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "payment")
    private List<Refund> refunds;

}
