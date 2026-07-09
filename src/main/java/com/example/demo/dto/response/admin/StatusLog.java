package com.example.demo.dto.response.admin;

import lombok.Data;

/**管理員：活動詳細：活動狀態log{@link com.example.demo.dto.response.admin.AdminEventDetailDto} */
@Data
public class StatusLog {
    /**狀態更動時的日期時間 */
    private String dateTime;
    /**更動後的狀態 */
    private String status;
    /**此次操作說明 */
    private String description;
    /**操作人員的角色類型(ADMIN || ORGANIZER) */
    private String operatorRole;
    /**操作人員名稱 */
    private String operatorName;
}