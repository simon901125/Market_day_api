package com.example.demo.dto.response.admin;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員的系統操作紀錄 */
@Schema(description = "管理員的系統操作紀錄")
@Data
public class AdminLogsDto {
    private List<AdminLog> adminLogs;

    /**管理員的系統操作紀錄項目 */
    @Data
    private class AdminLog {
        /**系統操作id */
        private Long id;
        /**系統操作時間 yyyy-MM-dd HH:mm */
        private String createdAt;
        /**操作人員名稱 */
        private String operator;
        /**操作類型 */
        private String actionType;
        /**操作對象 */
        private String target;
        /**操作說明 */
        private String detail;
    }
}
