package com.example.demo.Service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.MarketEventRepository;
import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MarketEventCardResponse;
import com.example.demo.dto.response.MarketEventDetailResponse;

@Service
public class MarketEventService {
    @Autowired
    private MarketEventRepository marketEventRepository;

    public ApiResponse<List<MarketEventCardResponse>> searchMarkets(MarketSearchRequest request) {
        if (!isValidEventType(request)) {
            return ApiResponse.fail("Invalid market search type");
        }

        if (!isValidEventStatuses(request)) {
            return ApiResponse.fail("Invalid market event status");
        }

        List<MarketEventCardResponse> markets = marketEventRepository.searchMarketEvents(request);
        return ApiResponse.success("Market events retrieved successfully", markets);
    }

    public ApiResponse<MarketEventDetailResponse> getMarketDetail(Long id) {
        if (id == null) {
            return ApiResponse.fail("Market id is required");
        }

        return marketEventRepository.findMarketEventDetailById(id)
                .map(detail -> ApiResponse.success("Market event detail retrieved successfully", detail))
                .orElseGet(() -> ApiResponse.fail("Market event not found"));
    }

    private boolean isValidEventType(MarketSearchRequest request) {
        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            return true;
        }

        return switch (request.eventType().trim().toUpperCase()) {
            case "CURRENT", "HISTORY", "ALL" -> true;
            default -> false;
        };
    }

    private boolean isValidEventStatuses(MarketSearchRequest request) {
        if (request == null || request.eventStatuses() == null) {
            return true;
        }

        return request.eventStatuses().stream()
                .filter(status -> status != null && !status.isBlank())
                .map(status -> status.trim().toUpperCase())
                .allMatch(status -> switch (status) {
                    case "UPCOMING", "STARTING_SOON", "ONGOING", "ENDED" -> true;
                    default -> false;
                });
    }
}
