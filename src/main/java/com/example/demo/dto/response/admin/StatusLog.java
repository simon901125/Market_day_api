package com.example.demo.dto.response.admin;

import com.example.demo.enums.Role;

import lombok.Data;


/**
 * 活動狀態Log，包含狀態更動時的日期時間、更動後的狀態、此次操作說明、操作人員的角色類型、操作人員名稱<br>
 * 用於前端頁面 管理員：活動詳細
 * @see AdminEventDetailDto#logs
 */
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

    public void setOperatorRole(Role operatorRole){
        if (!operatorRole.equals(Role.VENDOR)) {           
            this.operatorRole = operatorRole.name();
        }
    }

}