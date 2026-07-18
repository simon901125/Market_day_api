package com.example.demo.Repository;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImageStorageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ImageStorageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int updateVendorImage(String email, String columnName, String imageUrl) {
        if (!"avatar_image_url".equals(columnName) && !"cover_image_url".equals(columnName)) {
            throw new IllegalArgumentException("Unsupported vendor image column");
        }
        String sql = """
                UPDATE vp
                SET %s = :imageUrl
                FROM dbo.vendor_profiles vp
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                INNER JOIN dbo.users u ON u.id = up.user_id
                WHERE u.email = :email
                  AND u.role = 'VENDOR'
                  AND u.status <> 'IS_DELETED'
                """.formatted(columnName);
        return jdbcTemplate.update(sql, Map.of("email", email, "imageUrl", imageUrl));
    }

    public int updateProductImage(String email, Long productId, String imageUrl) {
        String sql = """
                UPDATE p
                SET image_url = :imageUrl
                FROM dbo.vendor_products p
                INNER JOIN dbo.vendor_profiles vp ON vp.id = p.vendor_profile_id
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                INNER JOIN dbo.users u ON u.id = up.user_id
                WHERE p.id = :productId
                  AND u.email = :email
                  AND u.role = 'VENDOR'
                  AND u.status <> 'IS_DELETED'
                """;
        return jdbcTemplate.update(sql, Map.of(
                "email", email,
                "productId", productId,
                "imageUrl", imageUrl));
    }

    public int updateEventImage(String email, Long eventId, String columnName, String imageUrl) {
        if (!"cover_image_url".equals(columnName) && !"map_image_url".equals(columnName)) {
            throw new IllegalArgumentException("Unsupported event image column");
        }
        String sql = """
                UPDATE e
                SET %s = :imageUrl
                FROM dbo.market_events e
                INNER JOIN dbo.users u ON u.id = e.user_id
                WHERE e.id = :eventId
                  AND u.email = :email
                  AND u.role = 'ORGANIZER'
                  AND u.status <> 'IS_DELETED'
                  AND e.workflow_status IN (N'DRAFT', N'REVISION_REQUIRED')
                """.formatted(columnName);
        return jdbcTemplate.update(sql, Map.of(
                "email", email,
                "eventId", eventId,
                "imageUrl", imageUrl));
    }
}
