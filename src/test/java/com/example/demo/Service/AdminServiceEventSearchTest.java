package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminEventsItemDto;
import com.example.demo.entity.Category;
import com.example.demo.entity.EventApplication;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;
import com.example.demo.enums.status.DepositStatus;
import com.example.demo.enums.status.PaymentStatus;
import com.example.demo.enums.status.ReviewStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.Role;

import jakarta.persistence.EntityManager;

/** 驗證改用EntityManager手動組multiselect查詢後,搜尋結果與狀態計算跟改動前的行為一致 */
@DataJpaTest
@Import(AdminService.class)
class AdminServiceEventSearchTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AdminService adminService;

    @Test
    void getEventsListFiltersByKeywordAndComputesRegisteredBoothCount() {
        LocalDateTime now = LocalDateTime.now();
        Category category = newCategory();

        User organizerUser = newUser("organizer@test.com");
        newUserProfile(organizerUser, "測試主辦方");

        MarketEvent matchingEvent = newEvent(category, organizerUser, "假日花市開幕");
        matchingEvent.setWorkflowStatus(WorkflowStatus.PUBLISHED);
        matchingEvent.setRegistrationStartAt(now.minusDays(2));
        matchingEvent.setRegistrationEndAt(now.plusDays(5));
        matchingEvent.setMaxBooths(10);
        matchingEvent.setStartAt(now.plusDays(10));
        matchingEvent.setEndAt(now.plusDays(12));
        matchingEvent.setCreateAt(now.minusDays(1));
        entityManager.persist(matchingEvent);

        // 已報名攤位數應該只算 APPROVED/PENDING,REJECTED不應該計入
        newApplication(matchingEvent, organizerUser, 1L, ReviewStatus.APPROVED);
        newApplication(matchingEvent, organizerUser, 2L, ReviewStatus.PENDING);
        newApplication(matchingEvent, organizerUser, 3L, ReviewStatus.REJECTED);

        User otherOrganizerUser = newUser("other@test.com");
        newUserProfile(otherOrganizerUser, "其他主辦方");
        MarketEvent otherEvent = newEvent(category, otherOrganizerUser, "夜市美食祭");
        entityManager.persist(otherEvent);

        entityManager.flush();
        entityManager.clear();

        AdminEventSearchDto request = new AdminEventSearchDto("花市", null, null, null, null);
        // pageNumber 從1開始計算
        PageResponse<AdminEventsItemDto> result = adminService.getEventsList(request, 1, 10);

        assertThat(result.getTotalItems()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        AdminEventsItemDto dto = result.getItems().get(0);
        assertThat(dto.getId()).isEqualTo(matchingEvent.getId());
        assertThat(dto.getName()).isEqualTo("假日花市開幕");
        assertThat(dto.getOrganizer()).isEqualTo("測試主辦方");
        assertThat(dto.getStartDate()).isEqualTo(matchingEvent.getStartAt().toLocalDate().toString());
        assertThat(dto.getEndDate()).isEqualTo(matchingEvent.getEndAt().toLocalDate().toString());
        // 報名開放中: 報名時間涵蓋現在,且已報名數(2,排除REJECTED) <= maxBooths(10)
        assertThat(dto.getStatus()).isEqualTo("REGISTRATION_OPEN");
    }

    private Category newCategory() {
        Category category = new Category();
        category.setName("市集");
        category.setSlug("market-" + System.nanoTime());
        category.setIsActive(true);
        entityManager.persist(category);
        return category;
    }

    private User newUser(String email) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setRole(Role.ORGANIZER);
        user.setEmail(email);
        user.setProvider(User.Provider.LOCAL);
        user.setStatus(UserStatus.ACTIVE);
        user.setIsLogin(false);
        user.setExpiredTime(now.plusDays(1));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        entityManager.persist(user);
        return user;
    }

    private UserProfile newUserProfile(User user, String name) {
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setProfileType(UserProfile.ProfileType.ORGANIZER);
        profile.setName(name);
        profile.setContactName(name);
        profile.setContactPhone("0900000000");
        entityManager.persist(profile);
        return profile;
    }

    private MarketEvent newEvent(Category category, User user, String title) {
        LocalDateTime now = LocalDateTime.now();
        MarketEvent event = new MarketEvent();
        event.addCategory(category);
        event.setUser(user);
        event.setTitle(title);
        event.setSummary("summary");
        event.setDescription("description");
        event.setLocationName("location");
        event.setCity("city");
        event.setAddress("address");
        event.setCreateAt(now);
        event.setStartAt(now.plusDays(1));
        event.setEndAt(now.plusDays(2));
        event.setRegistrationStartAt(now);
        event.setRegistrationEndAt(now.plusDays(1));
        event.setMaxBooths(5);
        event.setBaseFee(BigDecimal.TEN);
        event.setWorkflowStatus(WorkflowStatus.DRAFT);
        return event;
    }

    private void newApplication(MarketEvent event, User user, Long vendorProfileId, ReviewStatus reviewStatus) {
        EventApplication application = new EventApplication();
        application.setEvent(event);
        application.setUser(user);
        application.setVendorProfileId(vendorProfileId);
        application.setApplicationNo("APP-" + System.nanoTime());
        application.setTotalAmount(BigDecimal.TEN);
        application.setDepositAmount(BigDecimal.ZERO);
        application.setDepositStatus(DepositStatus.NOT_RETURNED);
        application.setReviewStatus(reviewStatus);
        application.setPaymentStatus(PaymentStatus.PENDING);
        application.setIsCancelled(false);
        application.setCreatedAt(LocalDateTime.now());
        entityManager.persist(application);
    }
}
