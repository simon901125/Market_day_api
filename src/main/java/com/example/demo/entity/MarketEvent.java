package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
import lombok.Data;

import com.example.demo.enums.WorkflowStatus;

/**
 * MarketEvent
 * 市集活動的Entity
 */
@Entity
@Data
@Table(name = "market_events")
public class MarketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**活動主辦方 */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_market_events_users"))
    private User user;

    /**活動類型 */
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "FK_market_events_categories"))
    private Category category;

    /**活動名稱 */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**活動摘要 */
    @Column(name = "summary", length = 300, nullable = false)
    private String summary;

    /**活動介紹 */
    @Column(name = "description", columnDefinition = "nvarchar(max)", nullable = false)
    private String description;

    /**地點名稱 */
    @Column(name = "location_name", length = 200, nullable = false)
    private String locationName;

    /**縣市 */
    @Column(name = "city", length = 50, nullable = false)
    private String city;

    /**地區 */
    @Column(name = "district", length = 50)
    private String district;

    /**地址 */
    @Column(name = "address", length = 255, nullable = false)
    private String address;

    /**交通方式 */
    @Column(name = "traffic_info", columnDefinition = "nvarchar(max)")
    private String trafficInfo;

    /**活動注意事項*/
    @Column(name = "notice", columnDefinition = "nvarchar(max)")
    private String notice;

    /**活動開始日期時間*/
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /**活動結束日期時間 */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /**報名開始時間 */
    @Column(name = "registration_start_at", nullable = false)
    private LocalDateTime registrationStartAt;

    /**報名結束時間 */
    @Column(name = "registration_end_at", nullable = false)
    private LocalDateTime registrationEndAt;

    /**攤位總數 */
    @Column(name = "max_booths", nullable = false)
    private Integer maxBooths;

    /**基本攤位費用 */
    @Column(name = "base_fee", precision = 10, scale = 2, nullable = false)
    private BigDecimal baseFee;

    /**活動封面url */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    /**活動攤位地圖底圖url */
    @Column(name = "map_image_url", length = 500)
    private String mapImageUrl;

    /**公開資訊時間 */
    @Column(name = "public_info_at")
    private LocalDateTime publicInfoAt;

    /**參與活動的品牌名單公開時間 */
    @Column(name = "brands_public_at")
    private LocalDateTime brandPublicAt;

    /**活動流程狀態 */
    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", length = 30, nullable = false)
    private WorkflowStatus workflowStatus;

    /**補件原因/審核備註 */
    @Column(name = "review_note", columnDefinition = "nvarchar(max)")
    private String reviewNote;

}
