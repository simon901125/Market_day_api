package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**管理員活動列表頁item */
@Schema(description = "管理員活動列表")
@Data
public class AdminEventsItemDto {

        /**活動id */
        private Long id;
        /**活動圖片url */
        private String imgUrl;
        /**活動名稱 */
        private String name;
        /**活動主辦方 */
        private String organizer;
        /**活動開始日期 yyyy-MM-dd */
        private String startDate;
        /**活動結束日期 yyyy-MM-dd */
        private String endDate;
        /**活動目前狀態 */
        private String status;
        /**活動創建時間 yyyy-MM-dd HH:mm*/
        private String createdAt;
        
}
