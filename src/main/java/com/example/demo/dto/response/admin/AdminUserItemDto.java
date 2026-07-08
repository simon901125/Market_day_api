package com.example.demo.dto.response.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

/**管理員使用者管理item */
@Schema(description = "管理員使用者管理列表")
@Data
public class AdminUserItemDto {

    /**使用者id */
    private Long id;
    /**使用者名稱 */
    private String name;
    /**使用者email */
    @Email
    private String email;
    /**使用者角色(參照 {@link com.example.demo.enums.Role}) */
    private String role;
    /**創建時間 yyyy-MM-dd HH:mm */
    private String createdAt;
    /**最後登入時間 yyyy-MM-dd HH:mm */
    private String lastLoginAt;
    /**使用者帳號狀態(參照 {@link com.example.demo.entity.User.Status}) */
    private String status;
}
