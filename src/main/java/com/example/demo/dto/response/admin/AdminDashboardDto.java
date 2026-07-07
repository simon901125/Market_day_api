package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員首頁 dashboard dto */
@Schema(description = "管理員首頁回應")
@Data
public class AdminDashboardDto {
    private int pendingReview, 
    mapBuilding, 
    pendingUnpublish,
    systemWarning, 
    totalOrganizer, 
    totalVender, 
    totalActivity,
    active;
}
