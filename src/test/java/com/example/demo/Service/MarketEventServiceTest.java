package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.MarketEventRepository;
import com.example.demo.dto.request.MarketSearchRequest;
import com.example.demo.dto.response.MarketEventDetailResponse;

@ExtendWith(MockitoExtension.class)
class MarketEventServiceTest {
    @Mock MarketEventRepository repository;
    MarketEventService service;

    @BeforeEach void setUp() {
        service = new MarketEventService();
        ReflectionTestUtils.setField(service, "marketEventRepository", repository);
    }

    @Test void acceptsNullAndSupportedSearchFilters() {
        when(repository.searchMarketEvents(any())).thenReturn(List.of());
        assertThat(service.searchMarkets(null, 1, 10).isSuccessStatus()).isTrue();
        MarketSearchRequest request = new MarketSearchRequest(null, null,
                List.of("活動進行中"), null, null, null, "目前活動");
        assertThat(service.searchMarkets(request, 1, 10).isSuccessStatus()).isTrue();
        verify(repository).searchMarketEvents(request);
    }

    @Test void rejectsUnsupportedEventTypeOrStatusBeforeRepository() {
        var badType = new MarketSearchRequest(null, null, null, null, null, null, "future");
        var badStatus = new MarketSearchRequest(null, null, List.of("CANCELLED"), null, null, null, null);
        var endedStatus = new MarketSearchRequest(null, null, List.of("已結束"), null, null, null, "目前活動");
        var multipleStatuses = new MarketSearchRequest(
                null, null, List.of("籌備中", "準備開始"), null, null, null, "目前活動");
        assertThat(service.searchMarkets(badType, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.searchMarkets(badStatus, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.searchMarkets(endedStatus, 1, 10).isSuccessStatus()).isFalse();
        assertThat(service.searchMarkets(multipleStatuses, 1, 10).isSuccessStatus()).isFalse();
        verify(repository, never()).searchMarketEvents(any());
    }

    @Test void detailReturnsClearMessagesForMissingIdAndNotFoundEvent() {
        assertThat(service.getMarketDetail(null).getMessage()).isEqualTo("請提供活動 ID");
        when(repository.findMarketEventDetailById(1L)).thenReturn(Optional.empty());
        assertThat(service.getMarketDetail(1L).getMessage())
                .isEqualTo("找不到活動 ID 1，或該活動尚未公開");
    }

    @Test void detailReturnsClearMessageWhenDateIsOutsideEventRange() {
        MarketEventDetailResponse detail = org.mockito.Mockito.mock(MarketEventDetailResponse.class);
        when(detail.brandsPublic()).thenReturn(true);
        when(detail.startDate()).thenReturn(LocalDate.of(2026, 8, 1));
        when(detail.endDate()).thenReturn(LocalDate.of(2026, 8, 2));
        when(repository.findMarketEventDetailById(2L)).thenReturn(Optional.of(detail));

        assertThat(service.getMarketDetail(2L, LocalDate.of(2026, 8, 3), null).getMessage())
                .isEqualTo("日期 2026-08-03 不在活動日期範圍 2026-08-01 至 2026-08-02 內");
    }

    @Test void detailReturnsClearMessageWhenStallNumberDoesNotExist() {
        MarketEventDetailResponse detail = org.mockito.Mockito.mock(MarketEventDetailResponse.class);
        when(detail.id()).thenReturn(3L);
        when(detail.brandsPublic()).thenReturn(true);
        when(detail.startDate()).thenReturn(LocalDate.of(2026, 8, 1));
        when(detail.endDate()).thenReturn(LocalDate.of(2026, 8, 2));
        when(repository.findMarketEventDetailById(3L)).thenReturn(Optional.of(detail));
        when(repository.findPublicSelectedStall(3L, LocalDate.of(2026, 8, 1), "Z99"))
                .thenReturn(Optional.empty());

        assertThat(service.getMarketDetail(3L, LocalDate.of(2026, 8, 1), " Z99 ").getMessage())
                .isEqualTo("活動 ID 3 在日期 2026-08-01 找不到攤位編號 Z99");
    }

    @Test void detailReturnsFoundEvent() {
        MarketEventDetailResponse detail = org.mockito.Mockito.mock(MarketEventDetailResponse.class);
        when(repository.findMarketEventDetailById(2L)).thenReturn(Optional.of(detail));
        assertThat(service.getMarketDetail(2L).getData().id()).isEqualTo(detail.id());
    }
}
