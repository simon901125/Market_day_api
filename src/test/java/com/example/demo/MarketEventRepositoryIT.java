package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.MarketEventRepository;
import com.example.demo.Repository.AutomaticStallAssignmentRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.request.MarketSearchRequest;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MarketEventRepositoryIT extends SqlServerIntegrationTestSupport {
    @Autowired MarketEventRepository repository;
    @Autowired AutomaticStallAssignmentRepository assignmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test void publishedEventCanBeSearchedAndLoadedByCurrentSchema() {
        Long eventId = createPublishedEvent();
        List<Map<String, Object>> categories = jdbc.queryForList("""
                SELECT c.id, c.name
                FROM market_event_categories mec
                INNER JOIN categories c ON c.id = mec.category_id
                WHERE mec.event_id = :eventId
                ORDER BY c.id
                """, Map.of("eventId", eventId));
        List<Long> categoryIds = categories.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        List<String> categoryNames = categories.stream().map(row -> row.get("name").toString()).toList();

        assertThat(categoryIds).hasSize(3);
        for (String categoryName : categoryNames) {
            var request = new MarketSearchRequest("Integration Market", "Taipei",
                    List.of("活動預告"), LocalDate.now(), LocalDate.now().plusDays(30), List.of(categoryName), "目前活動");
            var cards = repository.searchMarketEvents(request);
            assertThat(cards).extracting(card -> card.id()).contains(eventId);
            assertThat(cards.stream().filter(card -> card.id().equals(eventId)).findFirst().orElseThrow().categories())
                    .extracting(category -> category.id()).containsExactlyElementsOf(categoryIds);
        }
        var detail = repository.findMarketEventDetailById(eventId).orElseThrow();
        assertThat(detail.title()).isEqualTo("Integration Market");
        assertThat(detail.startTime()).isNotNull();
        assertThat(detail.categories()).extracting(category -> category.id()).containsExactlyElementsOf(categoryIds);
    }

    @Test void draftEventIsNotPublic() {
        Long eventId = createPublishedEvent();
        jdbc.update("UPDATE market_events SET workflow_status = 'DRAFT' WHERE id = :id", Map.of("id", eventId));
        assertThat(repository.findMarketEventDetailById(eventId)).isEmpty();
    }

    @Test void finalReviewEventRemainsVisibleInPublicSearchAndDetail() {
        Long eventId = createPublishedEvent();
        jdbc.update("""
                UPDATE market_events
                SET brands_public_at = DATEADD(MINUTE, -1, SYSDATETIME()),
                    map_image_url = N'/images/final-map.jpg'
                WHERE id = :id
                """, Map.of("id", eventId));

        var publishedDetail = repository.findMarketEventDetailById(eventId).orElseThrow();
        assertThat(publishedDetail.brandsPublic()).isTrue();
        assertThat(publishedDetail.mapImageUrl()).isEqualTo("/images/final-map.jpg");

        jdbc.update("UPDATE market_events SET workflow_status = 'FINAL_REVIEW' WHERE id = :id",
                Map.of("id", eventId));

        var cards = repository.searchMarketEvents(new MarketSearchRequest(
                "Integration Market", null, List.of(), null, null, List.of(), null));

        assertThat(cards).extracting(card -> card.id()).contains(eventId);
        var finalDetail = repository.findMarketEventDetailById(eventId).orElseThrow();
        assertThat(finalDetail.brandsPublic()).isTrue();
        assertThat(finalDetail.mapImageUrl()).isEqualTo("/images/final-map.jpg");
    }

    @Test void brandAndMapStayPrivateWithoutPublicationTime() {
        Long eventId = createPublishedEvent();
        jdbc.update("UPDATE market_events SET map_image_url = N'/images/private-map.jpg' WHERE id = :id",
                Map.of("id", eventId));

        var detail = repository.findMarketEventDetailById(eventId).orElseThrow();

        assertThat(detail.brandsPublic()).isFalse();
        assertThat(detail.mapImageUrl()).isNull();
    }

    @Test void futureBrandPublicationTimeStaysPrivateUntilReached() {
        Long eventId = createPublishedEvent();
        jdbc.update("""
                UPDATE market_events
                SET brands_public_at = DATEADD(HOUR, 1, SYSDATETIME()),
                    map_image_url = N'/images/future-map.jpg'
                WHERE id = :id
                """, Map.of("id", eventId));

        var detail = repository.findMarketEventDetailById(eventId).orElseThrow();

        assertThat(detail.brandsPublic()).isFalse();
        assertThat(detail.mapImageUrl()).isNull();
    }

    @Test void finishingFinalReviewStoresBrandPublicationTime() {
        Long eventId = createPublishedEvent();
        LocalDateTime publishedAt = LocalDateTime.now().withNano(0);

        assertThat(assignmentRepository.finishFinalReview(eventId, publishedAt)).isOne();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT workflow_status AS workflowStatus,
                       brands_public_at AS brandsPublicAt,
                       CAST(DATEADD(HOUR, 8, SYSUTCDATETIME()) AS DATETIME2(0)) AS databaseNow
                FROM market_events
                WHERE id = :id
                """, Map.of("id", eventId));
        assertThat(row).containsEntry("workflowStatus", "FINAL_REVIEW");
        LocalDateTime brandsPublicAt =
                ((java.sql.Timestamp) row.get("brandsPublicAt")).toLocalDateTime();
        LocalDateTime databaseNow =
                ((java.sql.Timestamp) row.get("databaseNow")).toLocalDateTime();
        assertThat(brandsPublicAt).isNotNull().isBeforeOrEqualTo(databaseNow);
    }

    private Long createPublishedEvent() {
        List<Long> categoryIds = jdbc.queryForList("SELECT TOP 3 id FROM categories ORDER BY id", Map.of(), Long.class);
        String email = "market-it-" + java.util.UUID.randomUUID() + "@example.test";
        Long organizerId = userRepository.createLocalUser("ORGANIZER", email, "hash");
        userRepository.markEmailVerified(organizerId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO market_events (
                    user_id, title, summary, description, location_name, city, district, address,
                    start_at, end_at, registration_start_at, registration_end_at, max_booths, base_fee,
                    workflow_status, traffic_info_metro)
                OUTPUT INSERTED.id
                VALUES (:userId, N'Integration Market', N'Summary', N'Description', N'Location',
                    N'Taipei', N'District', N'Address', :startAt, :endAt, :regStart, :regEnd, 10, 500,
                    N'PUBLISHED', N'Metro')
                """, new MapSqlParameterSource().addValue("userId", organizerId)
                        .addValue("startAt", now.plusDays(10).withHour(10)).addValue("endAt", now.plusDays(11).withHour(18))
                        .addValue("regStart", now.minusDays(1)).addValue("regEnd", now.plusDays(5)), Long.class);
        for (Long categoryId : categoryIds) {
            jdbc.update("""
                    INSERT INTO market_event_categories (event_id, category_id)
                    VALUES (:eventId, :categoryId)
                    """, Map.of("eventId", eventId, "categoryId", categoryId));
        }
        return eventId;
    }
}
