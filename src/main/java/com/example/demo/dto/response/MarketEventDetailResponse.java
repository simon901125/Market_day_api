package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record MarketEventDetailResponse(
        Long id,
        String title,
        String summary,
        String description,
        List<String> categoryNames,
        String locationName,
        String city,
        String district,
        String address,
        String trafficInfo,
        String notice,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime registrationStartAt,
        LocalDateTime registrationEndAt,
        Integer maxBooths,
        BigDecimal baseFee,
        String coverImageUrl,
        String mapImageUrl,
        LocalDateTime publicInfoAt,
        String reviewStatus,
        String publishStatus,
        String eventStatus) {
}
