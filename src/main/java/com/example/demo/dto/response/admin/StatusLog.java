package com.example.demo.dto.response.admin;

import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.type.Role;

/**
 * 活動狀態Log，包含狀態更動時的日期時間、更動後的狀態、此次操作說明、操作人員的角色類型、操作人員名稱<br>
 * 用於前端頁面 管理員：活動詳細
 * @param dateTime 狀態更動時的日期時間 yyyy/MM/dd HH:mm
 * @param status 更動後的狀態
 * @param description 此次操作說明
 * @param operator 操作人類型:操作人(ADMIN/ORGANIZER)
 * 
 * @see AdminEventDetailDto
 */
public record StatusLog(
    String dateTime,
    EventStatus status,
    String description,
    String operator
) {}