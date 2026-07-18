package com.example.demo.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 主辦方建立或修改活動時送出的完整活動資料。 */
public record OrganizerEventSaveRequest(
        Long eventId,
        String eventTitle,
        String summary,
        String description,
        List<Long> categoryIds,
        Schedule schedule,
        Location location,
        Booth booth,
        Equipment equipment) {

    public record Schedule(
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime registrationStartAt,
            LocalDateTime registrationEndAt) {}

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
            List<Zone> zones) {}

    public record Zone(
            Long zoneId,
            String zoneName,
            Integer stallCount,
            String colorCode) {}

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
