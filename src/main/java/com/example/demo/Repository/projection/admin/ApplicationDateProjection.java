package com.example.demo.Repository.projection.admin;

import java.time.LocalDate;

/**
 * 管理員:攤主詳細:活動報名紀錄的參與日期與已選定攤位
 * @param applicationId 所屬報名編號
 * @param applyDate 參與日期
 * @param stallNo 已選定的攤位編號(尚未選定時為null)
 * @param zoneName 已選定攤位所屬分區名稱(尚未選定時為null)
 *
 * @see com.example.demo.Repository.EventApplicationRepo#findApplicationDates(java.util.List)
 */
public record ApplicationDateProjection(
    Long applicationId,
    LocalDate applyDate,
    String stallNo,
    String zoneName
) {}
