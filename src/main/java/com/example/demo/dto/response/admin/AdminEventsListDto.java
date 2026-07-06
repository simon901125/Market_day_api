package com.example.demo.dto.response.admin;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員活動列表頁list */
@Schema(description = "管理員活動列表")
@Data
public class AdminEventsListDto {
    private List<AdminEventItemDto> adminEvents;


    /**管理員活動列表頁item */
    @Data
    private class AdminEventItemDto {
        /**活動id */
        private Long id;
        /**活動圖片url */
        private String imgUrl;
        /**活動名稱 */
        private String name;
        /**活動主辦方 */
        private String organizer;
        /**活動開始時間 yyyy-MM-dd HH:mm */
        private String startDate;
        /**活動結束時間 yyyy-MM-dd HH:mm */
        private String endDate;
        /**活動目前狀態 */
        private String status;
        /**活動創建時間 yyyy-MM-dd HH:mm*/
        private String createdAt;
    }
}
