package com.example.demo.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record MarketEventDetailResponse(
        Long id,
        String title,
        String coverImageUrl,
        String eventStatus,
        String summary,
        LocalDate startDate,
        String startDayOfWeek,
        LocalDate endDate,
        String endDayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        long durationDays,
        String locationName,
        String city,
        String district,
        String address,
        String description,
        List<CategoryResponse> categories,
        OrganizerInfo organizer,
        List<TrafficInfo> trafficInfos,
        boolean brandsPublic,
        String mapImageUrl,
        LocalDate selectedDate,
        SelectedStall selectedStall) {

    public record OrganizerInfo(
            String organizerName,
            String contactEmail,
            String contactPhone,
            String serviceDays,
            LocalTime serviceStartTime,
            LocalTime serviceEndTime) {
    }

    public record TrafficInfo(String method, String details) {
    }

    public record SelectedStall(
            String stallNo,
            StallBrand brand) {
    }

    public record StallBrand(
            Long vendorProfileId,
            String brandName,
            CategoryResponse category,
            String brandSummary,
            String facebookUrl,
            String instagramUrl,
            String websiteUrl,
            String avatarImageUrl,
            String coverImageUrl) {
    }
}
