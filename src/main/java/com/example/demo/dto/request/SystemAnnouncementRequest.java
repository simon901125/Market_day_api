package com.example.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemAnnouncementRequest(
        @NotBlank(message = "公告標題不可為空")
        @Size(max = 150, message = "公告標題不可超過 150 字")
        String title,
        @NotBlank(message = "公告內容不可為空")
        String content) {
}
