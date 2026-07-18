package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrganizerEventDetailResponse(
        Long eventId,
        String eventTitle,
        String summary,
        String description,
        List<Category> categories,
        String coverImageUrl,
        Schedule schedule,
        Location location,
        Booth booth,
        Equipment equipment,
        String workflowStatus,
        String status,
        String statusText,
        String reviewNote,
        List<String> availableActions,
        LocalDateTime createdAt) {

    public record Category(Long categoryId, String categoryName, String categorySlug) {}

    public record Schedule(
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime registrationStartAt,
            LocalDateTime registrationEndAt,
            LocalDateTime publicInfoAt,
            LocalDateTime brandsPublicAt) {}

    public record Location(
            String locationName,
            String city,
            String district,
            String address,
            String trafficInfoMetro,
            String trafficInfoBus,
            String trafficInfoDriving) {}

    public record Booth(
            Integer maxBooths,
            BigDecimal stallWidth,
            BigDecimal stallLength,
            BigDecimal baseFee,
            BigDecimal depositAmount,
            String mapImageUrl,
            List<Zone> zones) {}

    public record Zone(Long zoneId, String zoneName, Integer stallCount, String colorCode) {}

    public record Equipment(List<Item> items) {}

    public record Item(
            Long equipmentId,
            String equipmentGroupKey,
            String name,
            BigDecimal rentalFee,
            String pricingUnit,
            String unit,
            String chargeType,
            String itemType,
            String description,
            Integer stockQuantity,
            Integer perStallRentalLimit,
            String rentalStatus,
            Integer wattageLimit) {}
}
