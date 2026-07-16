package com.example.demo.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.example.demo.enums.status.UnpublishRequestStatus;

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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 活動下架申請的Entity。<br>
 * 包含申請原因、審核狀態、審核備註、申請時間、審核時間<br>
 * <b>FK</b>:申請下架的活動{@link MarketEvent}、提出申請的使用者{@link User}、審核申請的使用者{@link User}
 */
@Entity
@Data
@Table(name = "event_unpublish_requests")
public class EventUnpublishRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申請下架的活動 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "FK_event_unpublish_requests_market_events"))
    private MarketEvent event;

    /** 提出申請的使用者 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false, foreignKey = @ForeignKey(name = "FK_event_unpublish_requests_requested_by"))
    private User requestUser;

    /** 審核申請的使用者 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", foreignKey = @ForeignKey(name = "FK_event_unpublish_requests_reviewed_by"))
    private User reviewUser;

    /** 申請原因 */
    @Column(name = "reason", columnDefinition = "nvarchar(max)", nullable = false)
    private String reason;

    /** 審核狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private UnpublishRequestStatus status = UnpublishRequestStatus.PENDING;

    /** 審核備註 */
    @Column(name = "review_note", columnDefinition = "nvarchar(max)")
    private String reviewNote;

    /** 申請時間 */
    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    /** 審核時間 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

}
