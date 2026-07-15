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
import com.example.demo.Repository.UserRepository;
import com.example.demo.dto.request.MarketSearchRequest;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MarketEventRepositoryIT extends SqlServerIntegrationTestSupport {
    @Autowired MarketEventRepository repository;
    @Autowired UserRepository userRepository;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test void publishedEventCanBeSearchedAndLoadedByCurrentSchema() {
        Long eventId = createPublishedEvent();
        var request = new MarketSearchRequest("Integration Market", List.of("Taipei"),
                List.of("UPCOMING"), LocalDate.now(), LocalDate.now().plusDays(30), null, "CURRENT");
        var cards = repository.searchMarketEvents(request);
        assertThat(cards).extracting(card -> card.id()).contains(eventId);
        var detail = repository.findMarketEventDetailById(eventId).orElseThrow();
        assertThat(detail.title()).isEqualTo("Integration Market");
        assertThat(detail.startTime()).isNotNull();
        assertThat(detail.publishStatus()).isEqualTo("PUBLISHED");
    }

    @Test void draftEventIsNotPublic() {
        Long eventId = createPublishedEvent();
        jdbc.update("UPDATE market_events SET workflow_status = 'DRAFT' WHERE id = :id", Map.of("id", eventId));
        assertThat(repository.findMarketEventDetailById(eventId)).isEmpty();
    }

    private Long createPublishedEvent() {
        Long categoryId = jdbc.queryForObject("SELECT TOP 1 id FROM categories ORDER BY id", Map.of(), Long.class);
        String email = "market-it-" + java.util.UUID.randomUUID() + "@example.test";
        Long organizerId = userRepository.createLocalUser("ORGANIZER", email, "hash");
        userRepository.markEmailVerified(organizerId);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return jdbc.queryForObject("""
                INSERT INTO market_events (
                    user_id, category_id, title, summary, description, location_name, city, district, address,
                    start_at, end_at, registration_start_at, registration_end_at, max_booths, base_fee,
                    workflow_status, traffic_info_metro)
                OUTPUT INSERTED.id
                VALUES (:userId, :categoryId, N'Integration Market', N'Summary', N'Description', N'Location',
                    N'Taipei', N'District', N'Address', :startAt, :endAt, :regStart, :regEnd, 10, 500,
                    N'PUBLISHED', N'Metro')
                """, new MapSqlParameterSource().addValue("userId", organizerId).addValue("categoryId", categoryId)
                        .addValue("startAt", now.plusDays(10).withHour(10)).addValue("endAt", now.plusDays(11).withHour(18))
                        .addValue("regStart", now.minusDays(1)).addValue("regEnd", now.plusDays(5)), Long.class);
    }
}
