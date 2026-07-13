package com.example.demo.dto.response.admin;

import lombok.Data;

/**
 * 活動攤位分區。包含活動分區名稱、分區攤位數量
 * 
 * @see AdminEventDetailDto
 */
@Data
public class BoothZone {
    /**活動分區名稱 */
    private String name;
    /**分區攤位數量 */
    private int qty;
}
