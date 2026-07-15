package com.example.demo.dto.response;

public record StoredImageResponse(
        String purpose,
        Long productId,
        Long eventId,
        String imageUrl,
        String contentType,
        long fileSize) {
}
