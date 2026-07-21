package com.example.demo.Service;

import java.util.List;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.MarketEventRepository;
import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MarketEventCardResponse;
import com.example.demo.dto.response.MarketEventDetailResponse;
import com.example.demo.dto.response.PageResponse;

@Service
public class MarketEventService {
    @Autowired
    private MarketEventRepository marketEventRepository;

    public ApiResponse<PageResponse<MarketEventCardResponse>> searchMarkets(
            MarketSearchRequest request,
            Integer page,
            Integer pageSize) {
        if (!isValidEventType(request)) {
            return ApiResponse.fail("Invalid market search type");
        }

        if (!isValidEventStatuses(request)) {
            return ApiResponse.fail("Invalid market event status");
        }

        List<MarketEventCardResponse> markets = marketEventRepository.searchMarketEvents(request);
        return ApiResponse.success(
                "Market events retrieved successfully",
                PageResponse.from(markets, page, pageSize));
    }

    public ApiResponse<MarketEventDetailResponse> getMarketDetail(Long id, LocalDate date, String stallNo) {
        if (id == null) {
            return ApiResponse.fail("請提供活動 ID");
        }

        MarketEventDetailResponse detail = marketEventRepository.findMarketEventDetailById(id).orElse(null);
        if (detail == null) {
            return ApiResponse.fail("找不到活動 ID " + id + "，或該活動尚未公開");
        }
        if (!detail.brandsPublic()) {
            return ApiResponse.success("Market event detail retrieved successfully", withoutBrandPublication(detail));
        }

        LocalDate selectedDate = date == null ? detail.startDate() : date;
        if (selectedDate == null || selectedDate.isBefore(detail.startDate()) || selectedDate.isAfter(detail.endDate())) {
            return ApiResponse.fail("日期 " + selectedDate + " 不在活動日期範圍 "
                    + detail.startDate() + " 至 " + detail.endDate() + " 內");
        }

        String normalizedStallNo = stallNo == null ? null : stallNo.trim();
        var selectedStall = normalizedStallNo == null || normalizedStallNo.isEmpty()
                ? null
                : marketEventRepository.findPublicSelectedStall(detail.id(), selectedDate, normalizedStallNo)
                        .orElse(null);
        if (normalizedStallNo != null && !normalizedStallNo.isEmpty() && selectedStall == null) {
            return ApiResponse.fail("活動 ID " + id + " 在日期 " + selectedDate
                    + " 找不到攤位編號 " + normalizedStallNo);
        }

        MarketEventDetailResponse response = copyDetail(
                detail, true, detail.mapImageUrl(), selectedDate, selectedStall);
        return ApiResponse.success("Market event detail retrieved successfully", response);
    }

    public ApiResponse<MarketEventDetailResponse> getMarketDetail(Long id) {
        return getMarketDetail(id, null, null);
    }

    private MarketEventDetailResponse withoutBrandPublication(MarketEventDetailResponse detail) {
        return copyDetail(detail, false, null, null, null);
    }

    private MarketEventDetailResponse copyDetail(
            MarketEventDetailResponse detail,
            boolean brandsPublic,
            String mapImageUrl,
            LocalDate selectedDate,
            MarketEventDetailResponse.SelectedStall selectedStall) {
        return new MarketEventDetailResponse(
                detail.id(), detail.title(), detail.coverImageUrl(), detail.eventStatus(), detail.summary(),
                detail.startDate(), detail.startDayOfWeek(), detail.endDate(), detail.endDayOfWeek(),
                detail.startTime(), detail.endTime(), detail.durationDays(), detail.locationName(), detail.city(),
                detail.district(), detail.address(), detail.description(), detail.categories(), detail.organizer(),
                detail.trafficInfos(), brandsPublic, mapImageUrl, selectedDate, selectedStall);
    }

    private boolean isValidEventType(MarketSearchRequest request) {
        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            return true;
        }

        return switch (request.eventType().trim()) {
            case "目前活動", "歷史活動" -> true;
            default -> false;
        };
    }

    private boolean isValidEventStatuses(MarketSearchRequest request) {
        if (request == null || request.eventStatuses() == null) {
            return true;
        }

        List<String> statuses = request.eventStatuses().stream()
                .filter(status -> status != null && !status.isBlank())
                .map(String::trim)
                .toList();
        if (statuses.size() > 1) {
            return false;
        }

        boolean validStatuses = statuses.stream().allMatch(status -> switch (status) {
                    case "活動預告", "即將開始", "進行中", "已結束" -> true;
                    default -> false;
                });
        if (!validStatuses || statuses.isEmpty() || request.eventType() == null) {
            return validStatuses;
        }

        String eventType = request.eventType().trim();
        return switch (eventType) {
            case "目前活動" -> !statuses.contains("已結束");
            case "歷史活動" -> statuses.contains("已結束");
            default -> true;
        };
    }
}
