package com.example.demo.dto.response.admin;

/**
 * 活動報名紀錄項目
 * @param eventName 活動名稱
 * @param registrationDate 報名日期
 * @param registrationStatus 報名狀態
 * @param paymentStatus 付款狀態
 * @param booth 攤位（若為 null 顯示 "-"）//TODO:確認前端頁面
 */
public record AdminVenderRegDto(
    String eventName,
    String registrationDate,
    String registrationStatus,
    String paymentStatus,
    String booth
) {}
