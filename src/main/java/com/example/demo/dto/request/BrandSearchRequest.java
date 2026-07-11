package com.example.demo.dto.request;

public record BrandSearchRequest(
        String keyword,
        String categoryName,
        String marketName,
        Integer page,
        Integer pageSize) {
}
