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

        List<Map<String, Object>> rows = repository.searchBrands(
                new BrandSearchRequest("Integration", null, null, 1, 6), 0, 6);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.getFirst().get("brandId")).longValue()).isEqualTo(brandId);
        assertThat(repository.findProductSummaries(List.of(brandId))).hasSize(2);
        assertThat(repository.findBrandProducts(brandId)).hasSize(2);
        assertThat(repository.findBrandDetail(brandId)).isPresent();
        assertThat(repository.findBrandCategories()).isNotEmpty();
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
                INSERT INTO vendor_profiles (user_profile_id, brand_name, brand_summary)
                VALUES (:profileId, :name, N'Integration summary')
                """, new MapSqlParameterSource().addValue("profileId", profileId)
                        .addValue("name", name));
        Long brandId = jdbc.queryForObject(
                "SELECT id FROM vendor_profiles WHERE user_profile_id = :profileId",
                Map.of("profileId", profileId), Long.class);
        jdbc.update("""
                INSERT INTO vendor_profile_categories (vendor_profile_id, category_id)
                VALUES (:brandId, :categoryId)
                """, Map.of("brandId", brandId, "categoryId", categoryId));
        return brandId;
    }

    private void insertProduct(Long brandId, String name) {
        jdbc.update("""
                INSERT INTO vendor_products (vendor_profile_id, name, short_description, price)
                VALUES (:brandId, :name, N'description', 100)
                """, new MapSqlParameterSource().addValue("brandId", brandId).addValue("name", name));
    }
}
