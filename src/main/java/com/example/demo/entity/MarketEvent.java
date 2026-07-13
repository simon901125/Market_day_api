package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.demo.enums.status.WorkflowStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 市集活動的Entity。<br>
 * 包含名稱、摘要、詳細介紹、注意事項、交通方式、活動流程狀態、補件原因/審核備註<br>
 * <b>地點資訊</b>:縣市、地區、地址、地點名稱<br>
 * <b>時間資訊</b>:建立時間、公開資訊時間、報名開始時間、報名結束時間、參與活動的品牌名單公開時間、開始日期、結束日期<br>
 * <b>攤位共用資訊</b>:攤位總數、基本攤位費用<br>
 * <b>圖片</b>:活動封面url、活動攤位地圖底圖url<br>
 * <b>FK</b>:活動主辦方{@link User}、活動類型{@link Category}
 * 
 * @see com.example.demo.entity.EventApplication#event
 * @see com.example.demo.entity.EventStallZone#marketEvent
 * @see com.example.demo.entity.EventStall#marketEvent
 */
@Entity
@Data
@Table(name = "market_events")
public class MarketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 活動主辦方 */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_market_events_users"))
    private User user;

    /** 活動類型 */
    @ManyToMany
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "FK_market_events_categories"))
    private Set<Category> categories = new HashSet<>();

    public void addCategory(Category category){
        if (categories.add(category)) {
            category.getMarketEvents().add(this);
        }
    }

    /** 活動名稱 */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /** 活動摘要 */
    @Column(name = "summary", length = 300, nullable = false)
    private String summary;

    /** 活動介紹 */
    @Column(name = "description", columnDefinition = "nvarchar(max)", nullable = false)
    private String description;

    /** 地點名稱 */
    @Column(name = "location_name", length = 200, nullable = false)
    private String locationName;

    /** 縣市 */
    @Column(name = "city", length = 50, nullable = false)
    private String city;

    /** 地區 */
    @Column(name = "district", length = 50)
    private String district;

    /** 地址 */
    @Column(name = "address", length = 255, nullable = false)
    private String address;

    //TODO:要再確認資料庫 :AdminService:getEventDetail()
    //----------我是分隔線----------
    /** 交通方式 */
    @Column(name = "traffic_info", columnDefinition = "nvarchar(max)")
    private String trafficInfo;
    //----------我是分隔線----------

    /** 活動注意事項 */
    @Column(name = "notice", columnDefinition = "nvarchar(max)")
    private String notice;

    /** 活動開始日期時間 */
    @Column(name = "create_at", nullable = false)
    private LocalDateTime createAt;

    /** 活動建立時間 */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 活動結束日期時間 */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** 報名開始時間 */
    @Column(name = "registration_start_at", nullable = false)
    private LocalDateTime registrationStartAt;

    /** 報名結束時間 */
    @Column(name = "registration_end_at", nullable = false)
    private LocalDateTime registrationEndAt;

    /** 攤位總數 */
    @Column(name = "max_booths", nullable = false)
    private Integer maxBooths;

    /** 基本攤位費用 */
    @Column(name = "base_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFee;

    /** 活動封面url */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /** 活動攤位地圖底圖url */
    @Column(name = "map_image_url", length = 500)
    private String mapImageUrl;

    /** 公開資訊時間 */
    @Column(name = "public_info_at")
    private LocalDateTime publicInfoAt;

    /** 參與活動的品牌名單公開時間 */
    @Column(name = "brands_public_at")
    private LocalDateTime brandPublicAt;

    /** 活動流程狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", length = 30, nullable = false)
    private WorkflowStatus workflowStatus;

    /** 補件原因/審核備註 */
    @Column(name = "review_note", columnDefinition = "nvarchar(max)")
    private String reviewNote;

    // ----------其他Entity的FK----------

    /** 活動的攤位報名清單 */
    @OneToMany(mappedBy = "event")
    private List<EventApplication> eventApplications;

    /** 活動攤位分區清單 */
    @OneToMany(mappedBy = "marketEvent")
    private List<EventStallZone> eventStallZones;

    /** 活動攤位資訊 */
    @OneToMany(mappedBy = "marketEvent")
    private List<EventStall> eventStalls;

}
