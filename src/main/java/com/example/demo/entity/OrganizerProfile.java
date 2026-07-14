package com.example.demo.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 主辦方資料的Entity。<br>
 * 包含公司名稱、統一編號、服務星期、服務開始時間、服務結束時間、主辦方名稱<br>
 * <b>FK</b>: 使用者的共用個人資料{@link UserProfile}
 */
@Entity
@Data
@Table(name = "organizer_profiles")
public class OrganizerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**所屬使用者的共用個人資料 */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "FK_organizer_profiles_user_profiles"))
    private UserProfile userProfile;

    /**主辦方名稱 */
    @Column(name = "organizer_name", length = 150)
    private String organizerName;
    
    /**公司名稱 */
    @Column(name = "company_name", length = 150)
    private String companyName;

    /**統一編號 */
    @Column(name = "tax_id", length = 20)
    private String taxId;

    /**服務星期 */
    @Column(name = "service_days", length = 100)
    private String serviceDays;

    /**服務開始時間 */
    @Column(name = "service_start_time")
    private LocalTime serviceStartTime;

    /**服務結束時間 */
    @Column(name = "service_end_time")
    private LocalTime serviceEndTime;

}
