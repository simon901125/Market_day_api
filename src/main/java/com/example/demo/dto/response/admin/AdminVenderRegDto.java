package com.example.demo.dto.response.admin;

import java.util.List;

/**
 * 活動報名紀錄
 * @param eventName 活動名稱
 * @param regBooths 報名攤位
 * @param regStatus 報名狀態
 * @param paymentStatus 付款狀態
 */
public record AdminVenderRegDto(
    String eventName,
    String regStatus,
    String paymentStatus,
    List<RegBooth> regBooths
) {}
