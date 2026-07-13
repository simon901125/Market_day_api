package com.example.demo.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.demo.dto.request.BrandSearchRequest;

@Repository
public class BrandRepository {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<String> findBrandCategoryNames() {
        String sql = """
                SELECT DISTINCT c.name
                FROM dbo.vendor_profiles vp
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                    AND up.profile_type = N'VENDOR'
                INNER JOIN dbo.users u ON u.id = up.user_id
                INNER JOIN dbo.categories c ON c.id = vp.category_id
                WHERE u.status = 'ACTIVE'
                  AND c.is_active = 1
                ORDER BY c.name ASC
                """;

        return namedParameterJdbcTemplate.queryForList(sql, Map.of(), String.class);
    }

    public List<String> findParticipatedMarketNames() {
        String sql = """
                SELECT DISTINCT me.title
                FROM dbo.event_applications ea
                INNER JOIN dbo.vendor_profiles vp ON vp.id = ea.vendor_profile_id
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                    AND up.profile_type = N'VENDOR'
                INNER JOIN dbo.users u ON u.id = up.user_id
                INNER JOIN dbo.market_events me ON me.id = ea.event_id
                WHERE u.status = 'ACTIVE'
                  AND ea.review_status = N'APPROVED'
                  AND ea.is_cancelled = 0
                ORDER BY me.title ASC
                """;

        return namedParameterJdbcTemplate.queryForList(sql, Map.of(), String.class);
    }

    public List<Map<String, Object>> searchBrands(BrandSearchRequest request, int offset, int pageSize) {
        String sql = """
                WITH brand_rows AS (
                    SELECT
                        vp.id AS brandId,
                        vp.cover_image_url AS mainImageUrl,
                        vp.avatar_image_url AS avatarImageUrl,
                        vp.brand_name AS brandName,
                        c.id AS categoryId,
                        c.name AS categoryName,
                        vp.brand_summary AS brandSummary,
                        COALESCE(participation.participatedMarketCount, 0) AS participatedMarketCount
                    FROM dbo.vendor_profiles vp
                    INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                        AND up.profile_type = N'VENDOR'
                    INNER JOIN dbo.users u ON u.id = up.user_id
                    INNER JOIN dbo.categories c ON c.id = vp.category_id
                    OUTER APPLY (
                        SELECT COUNT(DISTINCT ea.event_id) AS participatedMarketCount
                        FROM dbo.event_applications ea
                        WHERE ea.vendor_profile_id = vp.id
                          AND ea.review_status = N'APPROVED'
                          AND ea.is_cancelled = 0
                    ) participation
                    WHERE u.status = 'ACTIVE'
                      AND (:categoryName IS NULL OR c.name LIKE N'%' + :categoryName + N'%')
                      AND (
                            :keyword IS NULL
                            OR vp.brand_name LIKE N'%' + :keyword + N'%'
                            OR vp.brand_summary LIKE N'%' + :keyword + N'%'
                            OR vp.brand_description LIKE N'%' + :keyword + N'%'
                            OR EXISTS (
                                SELECT 1
                                FROM dbo.vendor_products p
                                WHERE p.vendor_profile_id = vp.id
                                  AND (
                                        p.name LIKE N'%' + :keyword + N'%'
                                        OR p.short_description LIKE N'%' + :keyword + N'%'
                                        OR p.description LIKE N'%' + :keyword + N'%'
                                  )
                            )
                      )
                      AND (
                            :marketName IS NULL
                            OR EXISTS (
                                SELECT 1
                                FROM dbo.event_applications ea
                                INNER JOIN dbo.market_events me ON me.id = ea.event_id
                                WHERE ea.vendor_profile_id = vp.id
                                  AND ea.review_status = N'APPROVED'
                                  AND ea.is_cancelled = 0
                                  AND me.title LIKE N'%' + :marketName + N'%'
                            )
                      )
                )
                SELECT
                    brandId,
                    mainImageUrl,
                    avatarImageUrl,
                    brandName,
                    categoryId,
                    categoryName,
                    brandSummary,
                    participatedMarketCount,
                    COUNT(*) OVER() AS totalRows
                FROM brand_rows
                ORDER BY participatedMarketCount DESC, brandName ASC, brandId ASC
                OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY
                """;

        Map<String, Object> params = searchParams(request);
        params.put("offset", offset);
        params.put("pageSize", pageSize);

        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, params));
    }

    public List<Map<String, Object>> findFeaturedProductSummaries(List<Long> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT
                    p.vendor_profile_id AS brandId,
                    p.id AS productId,
                    p.name AS productName
                FROM dbo.vendor_products p
                WHERE p.vendor_profile_id IN (:brandIds)
                  AND p.status = N'ACTIVE'
                  AND p.is_featured = 1
                ORDER BY p.vendor_profile_id ASC, p.id ASC
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("brandIds", brandIds);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, params));
    }

    public Optional<Map<String, Object>> findBrandDetail(Long brandId) {
        String sql = """
                SELECT
                    vp.id AS brandId,
                    vp.cover_image_url AS mainImageUrl,
                    vp.avatar_image_url AS avatarImageUrl,
                    vp.brand_name AS brandName,
                    c.id AS categoryId,
                    c.name AS categoryName,
                    vp.brand_summary AS brandSummary,
                    COALESCE(participation.participatedMarketCount, 0) AS participatedMarketCount,
                    vp.brand_description AS brandDescription,
                    vp.instagram_url AS instagramUrl,
                    vp.facebook_url AS facebookUrl,
                    vp.website_url AS websiteUrl
                FROM dbo.vendor_profiles vp
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                    AND up.profile_type = N'VENDOR'
                INNER JOIN dbo.users u ON u.id = up.user_id
                INNER JOIN dbo.categories c ON c.id = vp.category_id
                OUTER APPLY (
                    SELECT COUNT(DISTINCT ea.event_id) AS participatedMarketCount
                    FROM dbo.event_applications ea
                    INNER JOIN dbo.market_events me ON me.id = ea.event_id
                    WHERE ea.vendor_profile_id = vp.id
                      AND ea.review_status = N'APPROVED'
                      AND ea.is_cancelled = 0
                      AND me.end_at < SYSDATETIME()
                ) participation
                WHERE vp.id = :brandId
                  AND u.status = 'ACTIVE'
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("brandId", brandId);
        return RepositoryResultMapper.normalizeOptional(
                namedParameterJdbcTemplate.queryForList(sql, params).stream().findFirst());
    }

    public List<Map<String, Object>> findRepresentativeProducts(Long brandId) {
        String sql = """
                SELECT
                    p.id AS productId,
                    p.image_url AS productImageUrl,
                    p.name AS productName,
                    p.price AS productPrice,
                    p.short_description AS productShortDescription
                FROM dbo.vendor_products p
                WHERE p.vendor_profile_id = :brandId
                  AND p.status = N'ACTIVE'
                ORDER BY p.is_featured DESC, p.id ASC
                OFFSET 0 ROWS FETCH NEXT 3 ROWS ONLY
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("brandId", brandId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, params));
    }

    public List<Map<String, Object>> findParticipatedMarkets(Long brandId) {
        String sql = """
                SELECT DISTINCT
                    me.id AS eventId,
                    me.title AS eventTitle,
                    me.start_at AS eventStartAt,
                    me.end_at AS eventEndAt
                FROM dbo.event_applications ea
                INNER JOIN dbo.market_events me ON me.id = ea.event_id
                WHERE ea.vendor_profile_id = :brandId
                  AND ea.review_status = N'APPROVED'
                  AND ea.is_cancelled = 0
                  AND me.end_at < SYSDATETIME()
                ORDER BY me.start_at DESC, me.id DESC
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("brandId", brandId);
        return RepositoryResultMapper.normalizeList(namedParameterJdbcTemplate.queryForList(sql, params));
    }

    private Map<String, Object> searchParams(BrandSearchRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", normalizeText(request == null ? null : request.keyword()));
        params.put("categoryName", normalizeText(request == null ? null : request.categoryName()));
        params.put("marketName", normalizeText(request == null ? null : request.marketName()));
        return params;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
