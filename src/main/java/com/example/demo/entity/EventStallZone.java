package com.example.demo.entity;

import java.util.List;

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
 * 活動攤位分區的Entity。<br>
 * 包含活動分區名稱、分區攤位數量<br>
 * <b>FK</b>: 市集活動{@link MarketEvent}
 * 
 * @see com.example.demo.entity.EventStall#zone
 */
@Entity
@Data
@Table(name = "event_stall_zones")
public class EventStallZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**對應活動 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "FK_event_stall_zones_market_events"))
    private MarketEvent marketEvent;

    /**分區名稱 */
    @Column(name = "zone_name", length = 50, nullable = false)
    private String zoneName;

    /**分區攤位數量 */
    @Column(name = "stall_count", nullable = false)
    private int stallCount;

    /** 前端色票選定後保存的 #RRGGBB 分區顏色。 */
    @Column(name = "zone_color", length = 20)
    private String colorCode;

    //----------其他Entity的FK----------

    /**分區攤位資訊 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "zone")
    private List<EventStall> eventStalls;
}
