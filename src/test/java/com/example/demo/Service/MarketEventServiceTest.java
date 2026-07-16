package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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
        assertThat(service.searchMarkets(null).isSuccessStatus()).isTrue();
        MarketSearchRequest request = new MarketSearchRequest(null, null,
                List.of("upcoming", "ONGOING", " "), null, null, null, "current");
        assertThat(service.searchMarkets(request).isSuccessStatus()).isTrue();
        verify(repository).searchMarketEvents(request);
    }

    @Test void rejectsUnsupportedEventTypeOrStatusBeforeRepository() {
        var badType = new MarketSearchRequest(null, null, null, null, null, null, "future");
        var badStatus = new MarketSearchRequest(null, null, List.of("CANCELLED"), null, null, null, null);
        assertThat(service.searchMarkets(badType).isSuccessStatus()).isFalse();
        assertThat(service.searchMarkets(badStatus).isSuccessStatus()).isFalse();
        verify(repository, never()).searchMarketEvents(any());
    }

    @Test void detailHandlesMissingIdNotFoundAndFound() {
        assertThat(service.getMarketDetail(null).isSuccessStatus()).isFalse();
        when(repository.findMarketEventDetailById(1L)).thenReturn(Optional.empty());
        assertThat(service.getMarketDetail(1L).isSuccessStatus()).isFalse();
        MarketEventDetailResponse detail = org.mockito.Mockito.mock(MarketEventDetailResponse.class);
        when(repository.findMarketEventDetailById(2L)).thenReturn(Optional.of(detail));
        assertThat(service.getMarketDetail(2L).getData()).isSameAs(detail);
    }
}
