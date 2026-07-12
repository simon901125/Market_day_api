package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.specification.AdminLogSpecification;
import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.Repository.specification.UserSpecification;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.admin.AdminEventsItemDto;
import com.example.demo.dto.response.admin.AdminLogDto;
import com.example.demo.dto.response.admin.AdminOrganizerDetailDto;
import com.example.demo.dto.response.admin.AdminUserItemDto;
import com.example.demo.dto.response.admin.AdminVenderDetailDto;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.Category;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.OrganizerProfile;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetTypeForFront;
import com.example.demo.enums.type.Role;
import com.example.demo.projection.admin.AdminEventItemProjection;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class AdminService implements AdminServiceInterface, EventStatusServiceInterface<Object> {
    @Autowired
    EventRepo eventRepo;

    @Autowired
    UserRepo userRepo;

    @Autowired
    AdminLogRepo logRepo;

    @PersistenceContext
    EntityManager entityManager;

    /** yyyy-MM-dd HH:mm */
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** yyyy-MM-dd */
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** HH:mm */
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // 設定管理員後台: 首頁資料統計部分
    @Override
    public AdminDashboardDto getDashboardResponse() {
        LocalDateTime now = LocalDateTime.now();

        // 塞資料
        AdminDashboardDto dto = new AdminDashboardDto();
        dto.setActive(eventRepo.countByEventStatusIsACTIVE(now));
        dto.setMapBuilding(eventRepo.countByWorkflowStatus(WorkflowStatus.MAP_BUILDING));
        dto.setPendingReview(eventRepo.countByWorkflowStatus(WorkflowStatus.PENDING_REVIEW));
        dto.setPendingUnpublish(eventRepo.countByWorkflowStatus(WorkflowStatus.UNPUBLISH_REQUESTED));
        dto.setSystemWarning(0);// TODO:補完系統警告計數
        dto.setTotalActivity(eventRepo.countByEventInPlatform(now));
        dto.setTotalOrganizer(userRepo.countByRoleAndStatus(Role.ORGANIZER, UserStatus.ACTIVE));
        dto.setTotalVender(userRepo.countByRoleAndStatus(Role.VENDOR, UserStatus.ACTIVE));
        return dto;
    }

    @Override
    public Object getNotice(String bookMark, int pageNumber, int pageSize) {
        // TODO:for 管理員後台通知中心
        throw new UnsupportedOperationException("Unimplemented method 'setNotice'");
    }

    // 設定管理員後台: 活動搜尋
    @Override
    public PageResponse<AdminEventsItemDto> getEventsList(AdminEventSearchDto request, int pageNumber, int pageSize) {
        // ----------只撈頁面需要用到的欄位，避免撈出整張表----------
        // 設定要join的表
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<MarketEvent> root = cq.from(MarketEvent.class);
        Join<MarketEvent, User> user = root.join("user");
        Join<User, UserProfile> userProfile = user.join("userProfile");

        // 設定搜尋條件
        Specification<MarketEvent> spec = EventSpecification.build(request);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }

        // 組裝select欄位
        cq.multiselect(
                root.get("id"),
                root.get("coverImageUrl"),
                root.get("title"),
                userProfile.get("name"),
                root.get("startAt"),
                root.get("endAt"),
                root.get("createAt"),
                root.get("workflowStatus"),
                root.get("registrationStartAt"),
                root.get("registrationEndAt"),
                root.get("brandPublicAt"),
                root.get("maxBooths"),
                EventSpecification.registeredBoothCountSubquery(root, cq, cb));
        // 設定orderBy: 活動創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createAt")));
        // 查詢結果(有設定limit)
        List<Tuple> rows = entityManager.createQuery(cq)
                .setFirstResult((pageNumber - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        // 另外查詢符合條件的總筆數
        long total = eventRepo.count(spec);

        // ----------設定回傳資料----------
        List<AdminEventsItemDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            LocalDateTime startAt = row.get(4, LocalDateTime.class);
            LocalDateTime endAt = row.get(5, LocalDateTime.class);

            AdminEventsItemDto dtoItem = new AdminEventsItemDto();
            dtoItem.setId(row.get(0, Long.class));
            dtoItem.setImgUrl(row.get(1, String.class));
            dtoItem.setName(row.get(2, String.class));
            dtoItem.setOrganizer(row.get(3, String.class));
            dtoItem.setStartDate(startAt.format(dateFormatter));
            dtoItem.setEndDate(endAt.format(dateFormatter));
            dtoItem.setCreatedAt(row.get(6, LocalDateTime.class).format(dateTimeFormatter));
            dtoItem.setStatus(checkEventStatus(
                    row.get(7, WorkflowStatus.class),
                    row.get(8, LocalDateTime.class),
                    row.get(9, LocalDateTime.class),
                    row.get(10, LocalDateTime.class),
                    startAt,
                    endAt,
                    row.get(11, Integer.class),
                    row.get(12, Long.class).intValue()).toString());
            dtoList.add(dtoItem);
        }

        PageResponse<AdminEventsItemDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // 設定管理員後台: 活動詳細
    @Override
    public AdminEventDetailDto getEventDetail(@NonNull Long eventId) {
        // ----------撈資料----------
        // FIXME:前端畫面的三種交通方式和marketEvent呈現一對一關係
        // FIXME:攤位長和寬也和marketEvent呈現一對一關係、攤位高在前端已經取消欄位
        MarketEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        UserProfile profile = event.getUser().getUserProfile();
        OrganizerProfile organizerProfile = profile.getOrganizerProfile();
        // ----------塞資料----------
        String eventTime = String.format(
                "%s ~ %s %s~%s",
                event.getStartAt().format(dateFormatter),
                event.getEndAt().format(dateFormatter),
                event.getStartAt().format(timeFormatter),
                event.getEndAt().format(timeFormatter));
        String serviceHours = String.format(
                "%s %s~$s",
                organizerProfile.getServiceDays(),
                organizerProfile.getServiceStartTime().format(timeFormatter),
                organizerProfile.getServiceEndTime().format(timeFormatter));
        Set<Category> categories = event.getCategories();
        Set<String> categorySet = new HashSet<>();
        for (Category category : categories) {
            categorySet.add(category.getName());
        }

        AdminEventDetailDto dto = new AdminEventDetailDto(
                event.getTitle(),
                event.getCoverImageUrl(),
                categorySet,
                eventTime,
                event.getLocationName(),
                String.format("%s%s%s", event.getCity(), event.getDistrict(), event.getAddress()),
                eventId,
                changeToEventStatus(event).getStatus(),
                event.getDescription(),
                event.getRegistrationStartAt().format(dateTimeFormatter),
                event.getRegistrationEndAt().format(dateTimeFormatter),
                event.getPublicInfoAt() == null ? "-" : event.getPublicInfoAt().format(dateTimeFormatter),
                organizerProfile.getCompanyName(),
                profile.getContactName(),
                profile.getContactPhone(),
                profile.getContactEmail(),
                String.format("%s%s%s", profile.getCity(), profile.getDistrict(), profile.getAddress()),
                organizerProfile.getTaxId(),
                serviceHours,
                null,
                null,
                null,
                null,
                event.getMaxBooths().toString(),
                event.getBaseFee().toString(),
                null,
                event.getMapImageUrl(),
                null);
        return dto;
    }

    @Override
    public PageResponse<?> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        // 只撈頁面需要用到的欄位，避免撈出整張表
        // 設定要join的表
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<User> root = cq.from(User.class);
        Join<User, UserProfile> userProfile = root.join("userProfile");
        // 設定搜尋條件
        Specification<User> spec = UserSpecification.build(request);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        // 組裝select欄位
        cq.multiselect(
                root.get("id").alias("id"),
                userProfile.get("name").alias("name"),
                root.get("email").alias("email"),
                root.get("role").alias("role"),
                root.get("createdAt").alias("createdAt"),
                // FIXME:確認User的各項欄位意義
                root.get("expiredTime").alias("lastLoginAt "),
                // --------------------
                root.get("status").alias("status")

        );
        // 設定orderBy: 帳號創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createdAt")));
        // 查詢結果(有設定limit)
        List<Tuple> rows = entityManager.createQuery(cq).setFirstResult((pageNumber - 1) * pageSize)
                .setMaxResults(pageSize).getResultList();
        // 另外查詢符合條件的總筆數
        long total = userRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminUserItemDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            AdminUserItemDto dtoItem = new AdminUserItemDto(
                    row.get("id", Long.class),
                    row.get("name", String.class),
                    row.get("email", String.class),
                    row.get("role", Role.class).getRole(),
                    row.get("createdAt", LocalDateTime.class),
                    row.get("lastLoginAt", LocalDateTime.class),
                    row.get("status", UserStatus.class).getStatus());

            dtoList.add(dtoItem);
        }
        PageResponse<AdminUserItemDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // for 管理員後台使用者詳細
    @Override
    public AdminVenderDetailDto getVenderDetail(Long userId) {
        // TODO: 設定管理員後台: 攤主詳細
        throw new UnsupportedOperationException("Unimplemented method 'setVenderDetail'");
    }

    @Override
    public AdminOrganizerDetailDto getOrganizerDetail(Long userId) {
        // TODO: 設定管理員後台: 主辦方詳細
        throw new UnsupportedOperationException("Unimplemented method 'setOrganizerDetail'");
    }

    @Override
    public PageResponse<?> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        // 只撈頁面需要用到的欄位，避免撈出整張表
        // 設定要join的表
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<AdminOperationLog> root = cq.from(AdminOperationLog.class);
        Join<AdminOperationLog, User> user = root.join("user");
        // 設定搜尋條件
        Specification<AdminOperationLog> spec = AdminLogSpecification.build(request);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        Expression<String> targetEmail = AdminLogSpecification.targetEmailSubquery(root, cq, cb);
        Expression<AdminTargetTypeForFront> targetTypeForFront = AdminLogSpecification.targetTypeForFrontExpression(root, cq, cb);
        // 組裝select欄位
        cq.multiselect(
                root.get("operationType").alias("operationType"),
                root.get("targetLabel").alias("targetName"),
                root.get("createdAt").alias("createdAt"),
                root.get("content").alias("content"),
                targetEmail.alias("email"),
                targetTypeForFront.alias("targetType")
            );
        // 設定orderBy: 操作時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createdAt")));
        // 查詢結果(有設定limit)
        List<Tuple> rows = entityManager.createQuery(cq).setFirstResult((pageNumber - 1) * pageSize)
                .setMaxResults(pageSize).getResultList();
        // 另外查詢符合條件的總筆數
        long total = logRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminLogDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            AdminLogDto dtoItem = new AdminLogDto(
                    null, //TODO:確認資料庫
                    row.get("operationType", AdminOperationType.class),
                    row.get("targetType", AdminTargetTypeForFront.class),
                    row.get("targetName", String.class),
                    row.get("email", String.class),
                    row.get("createdAt", LocalDateTime.class).format(dateTimeFormatter),
                    row.get("content", String.class));

            dtoList.add(dtoItem);
        }

        PageResponse<AdminLogDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    /**
     * @param data 型別要是:AdminEventItemProjection或是MarketEvent
     * @return 轉換後顯示在前端的活動狀態
     * @throws IllegalArgumentException 型別不符時拋出
     */
    @Override
    public EventStatus changeToEventStatus(Object data) {
        if (data instanceof AdminEventItemProjection) {
            AdminEventItemProjection projection = (AdminEventItemProjection) data;

            return checkEventStatus(
                    projection.getWorkflowStatus(),
                    projection.getRegStartTime(),
                    projection.getRegEndTime(),
                    projection.getBrandPublicAt(),
                    projection.getStartAt(),
                    projection.getEndAt(),
                    projection.getMaxBooth(),
                    projection.getEventApplicationsCount());
        } else if (data instanceof MarketEvent) {
            MarketEvent entity = (MarketEvent) data;

            return checkEventStatus(
                    entity.getWorkflowStatus(),
                    entity.getRegistrationStartAt(),
                    entity.getRegistrationEndAt(),
                    entity.getBrandPublicAt(),
                    entity.getStartAt(),
                    entity.getEndAt(),
                    entity.getMaxBooths(),
                    entity.getEventApplications().size());
        }
        throw new IllegalArgumentException("data須符合型別類型:AdminEventItemProjection或是MarketEvent");
    }

}
