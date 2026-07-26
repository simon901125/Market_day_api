package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "藍新金流官方入口網址")
public record OrganizerNewebPayPortalResponse(
        @Schema(description = "藍新會員註冊網址", example = "https://www.newebpay.com/main/registration")
        String registrationUrl,
        @Schema(description = "藍新會員登入網址", example = "https://www.newebpay.com/main/login_center/single_login")
        String loginUrl) {
}
