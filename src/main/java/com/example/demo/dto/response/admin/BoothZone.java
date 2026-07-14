package com.example.demo.dto.response.admin;

/**
 * 活動攤位分區。包含活動分區名稱、分區攤位數量
 * @param name 活動分區名稱
 * @param qty 分區攤位數量
 * 
 * @see AdminEventDetailDto
 */
public record BoothZone(
    String name,
    int qty
) {}
