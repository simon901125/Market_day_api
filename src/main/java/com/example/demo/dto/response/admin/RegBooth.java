package com.example.demo.dto.response.admin;

/**
 * 管理員:攤主詳細:活動報名紀錄
 * @param regDate 報名日期 yyyy/MM/dd
 * @param boothNo 攤位編號
 * 
 * @see AdminVenderRegDto
 */
public record RegBooth(
    String regDate,
    String boothNo
) {}
