package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 狀態變更紀錄的Entity。<br>
 * 包含異動對象類型(指向表格)、異動對象ID(指向PK)、異動欄位名稱(指向欄位)、異動後狀態(指向欄位值)<br>
 * <b>FK</b>:所屬API請求紀錄{@link RequestLog}
 */
@Entity
@Data
@Table(name = "status_logs")
public class StatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬API請求紀錄 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_log_id", nullable = false, foreignKey = @ForeignKey(name = "FK_status_logs_request_logs"))
    private RequestLog requestLog;

    /** 異動對象類型 */
    @Column(name = "target_type", length = 100, nullable = false)
    private String targetType;

    /** 異動對象ID */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 異動欄位名稱 */
    @Column(name = "status_field", length = 100, nullable = false)
    private String statusField;

    /** 異動後狀態 */
    @Column(name = "new_status", length = 100, nullable = false)
    private String newStatus;
}
