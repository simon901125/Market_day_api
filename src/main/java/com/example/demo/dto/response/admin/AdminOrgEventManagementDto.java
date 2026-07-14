package com.example.demo.dto.response.admin;

/**
 * 
 * 管理員:主辦方詳細:活動管理紀錄
 * @param eventName 活動名稱
 * @param eventDate 活動日期 yyyy/MM/dd - yyyy/MM/dd HH:mm-HH:mm
 * @param eventStatus 活動狀態
 * @param registrationCount 報名人數（格式範例：120/150、55/55 等）
 */
public record AdminOrgEventManagementDto(
     String eventName,
     String eventDate,
     String eventStatus,
     String registrationCount

) {}