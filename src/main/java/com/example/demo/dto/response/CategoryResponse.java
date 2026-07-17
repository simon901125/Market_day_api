package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分類資料")
public record CategoryResponse(Long id, String name, String slug) {
}
