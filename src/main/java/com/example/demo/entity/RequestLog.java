package com.example.demo.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * API請求紀錄的Entity。<br>
 * 包含請求使用者、HTTP方法、API路徑、回應狀態碼、請求時間<br>
 * <b>FK</b>:使用者{@link User}
 *
 * @see StatusLog#requestLog
 */
@Entity
@Data
@Table(name = "request_logs")
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 發出請求的使用者 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "FK_request_logs_users"))
    private User user;

    /** HTTP方法 */
    @Column(name = "method", length = 10, nullable = false)
    private String method;

    /** API路徑 */
    @Column(name = "path", length = 500, nullable = false)
    private String path;

    /** 回應狀態碼 */
    @Column(name = "status_code")
    private Integer statusCode;

    /** 請求時間 */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //----------我是分隔線----------

    /** 此請求觸發的狀態變更紀錄清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "requestLog")
    private List<StatusLog> statusLogs;
}
