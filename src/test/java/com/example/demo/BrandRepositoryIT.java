package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.BrandRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.request.BrandSearchRequest;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BrandRepositoryIT extends SqlServerIntegrationTestSupport {
    @Autowired BrandRepository repository;
    @Autowired UserRepository userRepository;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test void searchAndDetailReturnAllPublishedBrandProducts() {
        Long brandId = createBrand("brand-repository@example.test", "Integration Tea");
        insertProduct(brandId, "Tea A");
        insertProduct(brandId, "Tea B");
        createPublishedParticipation(brandId);

        List<Map<String, Object>> rows = repository.searchBrands(
                new BrandSearchRequest("Integration", null, null, 1, 6), 0, 6);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.getFirst().get("brandId")).longValue()).isEqualTo(brandId);
        assertThat(repository.findProductSummaries(List.of(brandId))).hasSize(2);
        assertThat(repository.findBrandProducts(brandId)).hasSize(2);
        assertThat(repository.findBrandDetail(brandId)).isPresent();
        assertThat(repository.findBrandCategories()).hasSize(7);
    }

    @Test void inactiveAccountIsExcludedFromSearchAndDetail() {
        Long brandId = createBrand("inactive-brand@example.test", "Inactive Brand");
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = :email",
                Map.of("email", "inactive-brand@example.test"));
        assertThat(repository.searchBrands(new BrandSearchRequest("Inactive", null, null, 1, 6), 0, 6)).isEmpty();
        assertThat(repository.findBrandDetail(brandId)).isEmpty();
    }

    private Long createBrand(String email, String name) {
        Long categoryId = jdbc.queryForObject("SELECT TOP 1 id FROM categories ORDER BY id", Map.of(), Long.class);
        Long userId = userRepository.createLocalUser("VENDOR", email, "hash");
        userRepository.markEmailVerified(userId);
        userRepository.createUserProfile(userId, "VENDOR", name, email);
        Long profileId = jdbc.queryForObject(
                "SELECT id FROM user_profiles WHERE user_id = :userId AND profile_type = 'VENDOR'",
                Map.of("userId", userId), Long.class);
        jdbc.update("""
                INSERT INTO vendor_profiles (user_profile_id, category_id, brand_name, brand_summary)
                VALUES (:profileId, :categoryId, :name, N'Integration summary')
                """, new MapSqlParameterSource().addValue("profileId", profileId)
                        .addValue("categoryId", categoryId)
                        .addValue("name", name));
        Long brandId = jdbc.queryForObject(
                "SELECT id FROM vendor_profiles WHERE user_profile_id = :profileId",
                Map.of("profileId", profileId), Long.class);
        return brandId;
    }

    private void insertProduct(Long brandId, String name) {
        jdbc.update("""
                INSERT INTO vendor_products (vendor_profile_id, name, short_description, price)
                VALUES (:brandId, :name, N'description', 100)
                """, new MapSqlParameterSource().addValue("brandId", brandId).addValue("name", name));
    }

    private void createPublishedParticipation(Long brandId) {
        Long organizerUserId = userRepository.createLocalUser(
                "ORGANIZER", "brand-repository-organizer@example.test", "hash");
        userRepository.markEmailVerified(organizerUserId);
        Long vendorUserId = jdbc.queryForObject("""
                SELECT up.user_id
                FROM dbo.vendor_profiles vp
                INNER JOIN dbo.user_profiles up ON up.id = vp.user_profile_id
                WHERE vp.id = :brandId
                """, Map.of("brandId", brandId), Long.class);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO dbo.market_events (
                    user_id, title, start_at, end_at,
                    registration_start_at, registration_end_at,
                    workflow_status, brands_public_at
                )
                OUTPUT INSERTED.id
                VALUES (
                    :organizerUserId, N'Integration Market',
                    DATEADD(DAY, -2, DATEADD(HOUR, 8, SYSUTCDATETIME())),
                    DATEADD(DAY, -1, DATEADD(HOUR, 8, SYSUTCDATETIME())),
                    DATEADD(DAY, -10, DATEADD(HOUR, 8, SYSUTCDATETIME())),
                    DATEADD(DAY, -3, DATEADD(HOUR, 8, SYSUTCDATETIME())),
                    N'FINAL_REVIEW', DATEADD(DAY, -2, DATEADD(HOUR, 8, SYSUTCDATETIME()))
                )
                """, Map.of("organizerUserId", organizerUserId), Long.class);
        jdbc.update("""
                INSERT INTO dbo.event_applications (
                    application_no, event_id, user_id, vendor_profile_id,
                    total_amount, review_status, payment_status, is_cancelled
                )
                VALUES (
                    N'BRAND-REPOSITORY-APP', :eventId, :vendorUserId, :brandId,
                    100, N'APPROVED', N'PAID', 0
                )
                """, Map.of(
                "eventId", eventId,
                "vendorUserId", vendorUserId,
                "brandId", brandId));
    }
}
