package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員首頁 dashboard dto */
@Schema(description = "管理員首頁回應")
@Data
public class AdminDashboardDto {
    /**狀態=待審核的活動數量 */
    private int pendingReview;
    /**狀態=地圖建置中的活動數量 */
    private int mapBuilding;
    /**狀態=申請下架的活動數量 */
    private int pendingUnpublish;
    /**系統警告數 */
    private int systemWarning;
    /**平台活動中的主辦方數量 */
    private int totalOrganizer;
    /**平台活動中的攤主數量 */
    private int totalVender;
    /**平台目前架上活動數量(狀態= PUBLISHED、FINAL_REVIEW && endAt > now) */
    private int totalActivity;
    /**狀態=活動中的活動數量 */
    private int active;
}
