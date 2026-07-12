package com.example.demo.entity;

import java.math.BigDecimal;

import com.example.demo.enums.status.StallStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 *
 * 活動攤位資訊Entity。 <br>
 * 包含攤位編號、攤位長度、攤位寬度、攤位高度、攤位狀態{@link StallStatus}<br>
 * <b>FK</b>: 市集活動{@link MarketEvent}、活動攤位分區{@link EventStallZone}
 *
 */
@Entity
@Data
@Table(name = "event_stalls", uniqueConstraints = @UniqueConstraint(name = "UQ_event_stalls_event_stall_no", columnNames = {
        "event_id", "stall_no" }))
public class EventStall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**市集活動 */
    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "FK_event_stalls_market_events"))
    private MarketEvent marketEvent;

    /**活動攤位分區 */
    @ManyToOne
    @JoinColumn(name = "zone_id", nullable = false, foreignKey = @ForeignKey(name = "FK_event_stalls_event_stall_zones"))
    private EventStallZone zone;

    /**攤位編號 */
    @Column(name = "stall_no", length = 30, nullable = false)
    private String stallNo;

    //TODO:要再確認資料庫 :AdminService:getEventDetail()
    //----------要再確認資料庫----------
    /**攤位寬度 */
    @Column(name = "width", precision = 6, scale = 2)
    private BigDecimal width;
    
    /**攤位長度 */
    @Column(name = "length", precision = 6, scale = 2)
    private BigDecimal length;
    
    /**攤位高度 */
    @Column(name = "height", precision = 6, scale = 2)
    private BigDecimal height;
    //----------要再確認資料庫----------

    /**攤位狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private StallStatus status = StallStatus.AVAILABLE;

}
