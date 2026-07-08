package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * UserProfile
 * 使用者資料(主辦方/攤主)的Entity
 */
@Entity
@Data
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**所屬使用者 */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "FK_user_profiles_users"))
    @JsonBackReference
    private User user;

    /**資料類型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", length = 30, nullable = false)
    private ProfileType profileType;

    /**名稱 */
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    /**聯絡人姓名 */
    @Column(name = "contact_name", length = 100, nullable = false)
    private String contactName;

    /**聯絡電話 */
    @Column(name = "contact_phone", length = 30, nullable = false)
    private String contactPhone;

    /**聯絡信箱 */
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    /**縣市 */
    @Column(name = "city", length = 50)
    private String city;

    /**地區 */
    @Column(name = "district", length = 50)
    private String district;

    /**地址 */
    @Column(name = "address", length = 255)
    private String address;

    /**使用者資料類型 */
    public enum ProfileType {
        /**主辦方 */
        ORGANIZER,
        /**攤主 */
        VENDOR;
    }  
}
