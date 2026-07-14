package com.example.demo.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 使用者共用個人資料的Entity。<br>
 * 包含使用者類型{@link ProfileType}、名稱<br>
 * <b>聯絡人相關</b>: 聯絡人姓名、聯絡電話、聯絡信箱<br>
 * <b>地址相關</b>: 縣市、地區、地址<br>
 * <b>FK</b>: 使用者{@link User}
 * 
 * @see OrganizerProfile#userProfile
 * @see VendorProfile#userProfile
 */
@Entity
@Data
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬使用者 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_user_profiles_users"))
    private User user;

    /** 使用者類型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", length = 30)
    private ProfileType profileType;

    /** 聯絡人姓名 */
    @Column(name = "contact_name", length = 100)
    private String contactName;

    /** 聯絡電話 */
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    /** 聯絡信箱 */
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    /** 縣市 */
    @Column(name = "city", length = 50)
    private String city;

    /** 地區 */
    @Column(name = "district", length = 50)
    private String district;

    /** 地址 */
    @Column(name = "address", length = 255)
    private String address;

    //----------我是分隔線----------

    /**主辦方專用詳細資料 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "userProfile", fetch = FetchType.LAZY)
    private OrganizerProfile organizerProfile;

    public void setOrganizerProfile(OrganizerProfile organizerProfile) {
        this.organizerProfile = organizerProfile;
        if (organizerProfile != null) {
            organizerProfile.setUserProfile(this);
        }
    }

    /**攤主專用詳細資料 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(mappedBy = "userProfile", fetch = FetchType.LAZY)
    private VendorProfile vendorProfile;

    public void setVendorProfile(VendorProfile vendorProfile) {
        this.vendorProfile = vendorProfile;
        if (vendorProfile != null) {
            vendorProfile.setUserProfile(this);
        }
    }

    /** 使用者資料類型 */
    public enum ProfileType {
        /** 主辦方 */
        ORGANIZER,
        /** 攤主 */
        VENDOR;
    }
}
