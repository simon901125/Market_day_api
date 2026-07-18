package com.example.demo.dto.response;

import java.time.LocalDate;
import java.util.List;

public record MarketEventCardResponse(
    Long id,
    String title,
    String summary,
    String locationName,
    String city,
    String district,
    String address,
    LocalDate startDate,
    String startDayOfWeek,
    LocalDate endDate,
    String endDayOfWeek,
    String coverImageUrl,
    List<CategoryResponse> categories,
    String eventStatus) {
}
