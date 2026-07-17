package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import com.example.demo.enums.status.WorkflowStatus;

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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 市集活動的Entity。<br>
 * 包含名稱、摘要、詳細介紹、注意事項、交通方式(開車、公車、捷運)、活動流程狀態、補件原因/審核備註<br>
 * <b>地點資訊</b>:縣市、地區、地址、地點名稱<br>
 * <b>時間資訊</b>:建立時間、公開資訊時間、報名開始時間、報名結束時間、參與活動的品牌名單公開時間、開始日期、結束日期<br>
 * <b>攤位共用資訊</b>:攤位總數、基本攤位費用、攤位寬度、攤位長度<br>
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
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_market_events_users"))
    private User user;

    /** 活動類型 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "market_event_categories",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    public void addCategory(Category category){
        this.categories.add(category);
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

    /** 活動注意事項 */
    @Column(name = "notice", columnDefinition = "nvarchar(max)")
    private String notice;

    /**活動開始日期時間 */
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

    /** 本活動固定攤位寬度 */
    @Column(name = "stall_width", precision = 6, scale = 2)
    private BigDecimal stallWidth;

    /** 本活動固定攤位長度 */
    @Column(name = "stall_length", precision = 6, scale = 2)
    private BigDecimal stallLength;

    /** 基本攤位費用 */
    @Column(name = "base_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFee;

    @Column(name = "deposit_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /** 開車交通資訊 */
    @Column(name = "traffic_info_driving", columnDefinition = "nvarchar(max)")
    private String driving; 

    /** 公車交通資訊 */
    @Column(name = "traffic_info_bus", columnDefinition = "nvarchar(max)")
    private String bus;   

    /** 捷運交通資訊 */
    @Column(name = "traffic_info_metro", columnDefinition = "nvarchar(max)")
    private String metro;
    
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
    private WorkflowStatus workflowStatus = WorkflowStatus.DRAFT;
    
    /** 補件原因/審核備註 */
    @Column(name = "review_note", columnDefinition = "nvarchar(max)")
    private String reviewNote;
    
    /** 活動建立時間 */
    @CreationTimestamp
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createAt;
    // ----------其他Entity的FK----------
    
    /** 活動的攤位報名清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "event")
    private List<EventApplication> eventApplications;

    /** 活動攤位分區清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "marketEvent")
    private List<EventStallZone> eventStallZones;

    /** 活動攤位資訊 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "marketEvent")
    private List<EventStall> eventStalls;

}
