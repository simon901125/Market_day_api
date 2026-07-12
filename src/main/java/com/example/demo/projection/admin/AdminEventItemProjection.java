package com.example.demo.projection.admin;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;

import com.example.demo.enums.status.WorkflowStatus;

public interface AdminEventItemProjection {
    /**活動id */
    Long getId();

    /**活動圖片url */
    @Value("#{target.coverImageUrl}")
    String getImgUrl();

    /**活動名稱 */
    @Value("#{target.title}")
    String getName();

    /**活動主辦方 */
    @Value("#{target.user.userProfile.name}")
    String getOrganizer();

    /**活動報名開始時間 */
    @Value("#{target.registrationStartAt}")
    LocalDateTime getRegStartTime();
    
    /**活動報名結束時間 */
    @Value("#{target.registrationEndAt}")
    LocalDateTime getRegEndTime();

    /**參與活動的品牌名單公開時間 */
    LocalDateTime getBrandPublicAt();

    /**活動開始時間 */
    LocalDateTime getStartAt();

    /**活動結束時間 */
    LocalDateTime getEndAt();

    /**活動目前狀態 */
    WorkflowStatus getWorkflowStatus();

    /**活動創建時間 */
    @Value("#{target.createAt}")
    LocalDateTime getCreateAt();

    /**活動攤位總數 */
    @Value("#{target.maxBooths}")
    Integer getMaxBooth();

    /**活動已報名攤位數 */
    @Value("#{@eventRepo.countRegBoothsByEventId(target.id)}")
    int getEventApplicationsCount();

}
