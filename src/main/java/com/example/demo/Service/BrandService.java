package com.example.demo.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.BrandRepository;
import com.example.demo.dto.request.BrandSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.BrandDetailResponse;
import com.example.demo.dto.response.BrandSearchResponse;
import com.example.demo.dto.response.BrandSummaryResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.PageResponse;

@Service
public class BrandService {

    private static final int DEFAULT_BRAND_PAGE = 1;
    private static final int DEFAULT_BRAND_PAGE_SIZE = 6;
    private static final int MAX_BRAND_PAGE_SIZE = 6;

    @Autowired
    private BrandRepository brandRepository;

    public ApiResponse<MapBackedResponse> getBrandScrollOptions() {
        Map<String, Object> options = orderedMap(
                "categoryNames", brandRepository.findBrandCategoryNames(),
                "marketNames", brandRepository.findParticipatedMarketNames());

        return ApiResponse.success(
                "Brand scroll options retrieved successfully",
                new MapBackedResponse(options));
    }

    public ApiResponse<BrandSearchResponse> searchBrands(BrandSearchRequest request) {
        int page = normalizePage(request == null ? null : request.page());
        int pageSize = normalizePageSize(request == null ? null : request.pageSize());
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> rows = brandRepository.searchBrands(request, offset, pageSize);
        long totalItems = rows.isEmpty() ? 0 : numberValue(rows.get(0).get("totalRows")).longValue();
        Map<Long, List<Map<String, Object>>> productsByBrandId = productsByBrandId(rows);
        List<BrandSummaryResponse> brands = rows.stream()
                .map(this::withoutTotalRows)
                .map(row -> withRepresentativeProducts(row, productsByBrandId))
                .map(BrandSummaryResponse::new)
                .toList();

        return ApiResponse.success(
                "Brands retrieved successfully",
                new BrandSearchResponse(new PageResponse<>(brands, page, pageSize, totalItems)));
    }

    public ApiResponse<BrandDetailResponse> getBrandDetail(Long brandId) {
        if (brandId == null) {
            return ApiResponse.fail("Brand id is required");
        }

        Map<String, Object> brand = brandRepository.findBrandDetail(brandId).orElse(null);
        if (brand == null) {
            return ApiResponse.fail("Brand not found");
        }

        Map<String, Object> response = orderedMap(
                "brandId", brand.get("brandId"),
                "mainImageUrl", brand.get("mainImageUrl"),
                "avatarImageUrl", brand.get("avatarImageUrl"),
                "brandName", brand.get("brandName"),
                "categoryId", brand.get("categoryId"),
                "categoryName", brand.get("categoryName"),
                "brandSummary", brand.get("brandSummary"),
                "participatedMarketCount", brand.get("participatedMarketCount"),
                "brandDescription", brand.get("brandDescription"),
                "representativeProducts", brandRepository.findBrandProducts(brandId),
                "participatedMarkets", brandRepository.findParticipatedMarkets(brandId),
                "links", orderedMap(
                        "instagramUrl", brand.get("instagramUrl"),
                        "facebookUrl", brand.get("facebookUrl"),
                        "websiteUrl", brand.get("websiteUrl")));

        return ApiResponse.success("Brand detail retrieved successfully", new BrandDetailResponse(response));
    }

    private Map<String, Object> withoutTotalRows(Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>(row);
        values.remove("totalRows");
        return values;
    }

    private Map<String, Object> withRepresentativeProducts(
            Map<String, Object> brand,
            Map<Long, List<Map<String, Object>>> productsByBrandId) {
        Map<String, Object> values = new LinkedHashMap<>(brand);
        Long brandId = longValue(values.get("brandId"));
        values.put("representativeProducts", productsByBrandId.getOrDefault(brandId, List.of()));
        return values;
    }

    private Map<Long, List<Map<String, Object>>> productsByBrandId(List<Map<String, Object>> brands) {
        List<Long> brandIds = brands.stream()
                .map(row -> longValue(row.get("brandId")))
                .filter(id -> id != null)
                .toList();

        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> product : brandRepository.findProductSummaries(brandIds)) {
            Long brandId = longValue(product.get("brandId"));
            if (brandId == null) {
                continue;
            }

            Map<String, Object> productSummary = new LinkedHashMap<>(product);
            productSummary.remove("brandId");
            grouped.computeIfAbsent(brandId, id -> new java.util.ArrayList<>()).add(productSummary);
        }
        return grouped;
    }

    private Number numberValue(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_BRAND_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_BRAND_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_BRAND_PAGE_SIZE);
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
