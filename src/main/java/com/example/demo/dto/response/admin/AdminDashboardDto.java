package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 
 * 管理員:dashboard首頁
 * 
 * @param pendingReview    狀態=待審核的活動數量
 * @param mapBuilding      狀態=地圖建置中的活動數量
 * @param pendingUnpublish 狀態=申請下架的活動數量
 * @param systemWarning    系統警告數
 * @param totalOrganizer   平台活動中的主辦方數量
 * @param totalVender      平台活動中的攤主數量
 * @param totalActivity    平台目前架上活動數量(狀態= PUBLISHED、FINAL_REVIEW && endAt > now)
 * @param active           狀態=活動中的活動數量
 */
@Schema(description = "管理員首頁回應")
public record AdminDashboardDto(
        int pendingReview,
        int mapBuilding,
        int pendingUnpublish,
        int systemWarning,
        int totalOrganizer,
        int totalVender,
        int totalActivity,
        int active) {
}
