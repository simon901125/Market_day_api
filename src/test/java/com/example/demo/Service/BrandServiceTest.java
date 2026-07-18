package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.BrandRepository;
import com.example.demo.dto.request.BrandSearchRequest;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {
    @Mock BrandRepository repository;
    BrandService service;

    @BeforeEach void setUp() {
        service = new BrandService();
        ReflectionTestUtils.setField(service, "brandRepository", repository);
    }

    @Test void scrollOptionsCombinesBothRepositoryLists() {
        when(repository.findBrandCategories()).thenReturn(List.of(map("id", 1L, "name", "Food", "slug", "food")));
        when(repository.findParticipatedMarketNames("Food")).thenReturn(List.of("Market A"));
        var response = service.getBrandScrollOptions("Food");
        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().getValues()).containsKey("categories")
                .containsEntry("marketNames", List.of("Market A"));
    }

    @Test void searchNormalizesPagingAndGroupsAllProductsByBrand() {
        Map<String, Object> brand = map("brandId", 2, "brandName", "Tea", "totalRows", 8L);
        when(repository.searchBrands(any(), eq(0), eq(6))).thenReturn(List.of(brand));
        when(repository.findProductSummaries(List.of(2L))).thenReturn(List.of(
                map("brandId", 2L, "productId", 10L, "productName", "A"),
                map("brandId", 2L, "productId", 11L, "productName", "B")));
        var response = service.searchBrands(new BrandSearchRequest(null, null, null, 0, 99));
        var page = response.getData().getBrands();
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(6);
        assertThat(page.getTotalItems()).isEqualTo(8);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().getFirst().getValues().get("representativeProducts")).asList().hasSize(2);
    }

    @Test void emptySearchReturnsEmptyPageAndQueriesNoProductIds() {
        when(repository.searchBrands(null, 0, 6)).thenReturn(List.of());
        var response = service.searchBrands(null);
        assertThat(response.getData().getTotalCount()).isZero();
        verify(repository).findProductSummaries(List.of());
    }

    @Test void detailRejectsMissingIdAndUnknownBrand() {
        assertThat(service.getBrandDetail(null).isSuccessStatus()).isFalse();
        when(repository.findBrandDetail(4L)).thenReturn(Optional.empty());
        assertThat(service.getBrandDetail(4L).isSuccessStatus()).isFalse();
    }

    @Test void detailIncludesProductsMarketsAndLinks() {
        when(repository.findBrandDetail(4L)).thenReturn(Optional.of(map(
                "brandId", 4L, "brandName", "Tea", "instagramUrl", "ig", "facebookUrl", "fb", "websiteUrl", "web")));
        when(repository.findBrandProducts(4L)).thenReturn(List.of(map("productId", 1L)));
        when(repository.findParticipatedMarkets(4L)).thenReturn(List.of(map("eventId", 3L)));
        var values = service.getBrandDetail(4L).getData().getValues();
        assertThat(values.get("representativeProducts")).asList().hasSize(1);
        assertThat(values.get("participatedMarkets")).asList().hasSize(1);
        assertThat(values.get("links")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("instagramUrl", "ig");
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }
}
