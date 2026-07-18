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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 攤主資料的Entity。<br>
 * 包含品牌名稱、Instagram連結、Facebook連結、官網連結、品牌簡介、品牌詳細介紹<br>
 * <b>FK</b>: 使用者的共用個人資料{@link UserProfile}、攤位分類{@link Category}
 */
@Entity
@Data
@Table(name = "vendor_profiles")
public class VendorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**所屬使用者的共用個人資料 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "FK_vendor_profiles_user_profiles"))
    private UserProfile userProfile;

    /** 品牌分類 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_vendor_profiles_categories"))
    private Category category;

    /**品牌名稱 */
    @Column(name = "brand_name", length = 150)
    private String brandName;

    /**Instagram連結 */
    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    /**Facebook連結 */
    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    /**官網連結 */
    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    /**品牌簡介 */
    @Column(name = "brand_summary", length = 300)
    private String brandSummary;

    /**品牌詳細介紹 */
    @Column(name = "brand_description", columnDefinition = "nvarchar(max)")
    private String brandDescription;

    /**此攤主的攤位報名清單 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "vendorProfile")
    private List<EventApplication> eventApplications;

}
