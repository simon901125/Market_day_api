package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminEventDetailProjection;
import com.example.demo.Repository.specification.AdminLogSpecification;
import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.Repository.specification.UserSpecification;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.admin.AdminEventListDto;
import com.example.demo.dto.response.admin.AdminOperationLogDto;
import com.example.demo.dto.response.admin.AdminOrgDetailDto;
import com.example.demo.dto.response.admin.AdminOrgEventManagementDto;
import com.example.demo.dto.response.admin.AdminUserListDto;
import com.example.demo.dto.response.admin.AdminUserLoginDto;
import com.example.demo.dto.response.admin.AdminVenderDetailDto;
import com.example.demo.dto.response.admin.AdminVenderRegDto;
import com.example.demo.dto.response.admin.BoothZone;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.EventStallZone;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetTypeForFront;
import com.example.demo.enums.type.Role;

import jakarta.annotation.Nonnull;
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
    EventStallZoneRepo eventStallZoneRepo;

    @Autowired
    UserRepo userRepo;

    @Autowired
    AdminLogRepo logRepo;

    @PersistenceContext
    EntityManager entityManager;

    /** yyyy/MM/dd HH:mm */
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    /** yyyy/MM/dd */
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /** HH:mm */
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    // 設定管理員後台: 首頁資料統計部分
    @Override
    public AdminDashboardDto getDashboardResponse() {
        LocalDateTime now = LocalDateTime.now();

        // 塞資料
        return new AdminDashboardDto(
            eventRepo.countByWorkflowStatus(WorkflowStatus.PENDING_REVIEW), 
            eventRepo.countByWorkflowStatus(WorkflowStatus.MAP_BUILDING), 
            eventRepo.countByWorkflowStatus(WorkflowStatus.UNPUBLISH_REQUESTED), 
            0, // TODO:補完系統警告計數
            userRepo.countByRoleAndStatus(Role.ORGANIZER, UserStatus.ACTIVE), 
            userRepo.countByRoleAndStatus(Role.VENDOR, UserStatus.ACTIVE), 
            eventRepo.countByEventInPlatform(now), 
            eventRepo.countByEventStatusIsACTIVE(now));
    }

    @Override
    public Object getNotice(String bookMark, int pageNumber, int pageSize) {
        // TODO:for 管理員後台通知中心
        throw new UnsupportedOperationException("Unimplemented method 'setNotice'");
    }

    // 設定管理員後台: 活動搜尋
    @Override
    public PageResponse<AdminEventListDto> getEventsList(AdminEventSearchDto request, int pageNumber, int pageSize) {
        // ----------只撈頁面需要用到的欄位，避免撈出整張表----------
        // 設定要join的表
        TupleQueryContext<MarketEvent> ctx = newTupleQuery(MarketEvent.class);
        CriteriaBuilder cb = ctx.cb();
        CriteriaQuery<Tuple> cq = ctx.cq();
        Root<MarketEvent> root = ctx.root();
        Join<MarketEvent, User> user = root.join("user");
        Join<User, UserProfile> userProfile = user.join("userProfile");

        // 設定搜尋條件
        Specification<MarketEvent> spec = EventSpecification.build(request);
        applyPredicate(root, cq, cb, spec);

        // 組裝select欄位
        cq.multiselect(
                root.get("id").alias("id"),
                root.get("coverImageUrl").alias("coverImageUrl"),
                root.get("title").alias("title"),
                userProfile.get("name").alias("organizerName"),
                root.get("startAt").alias("startAt"),
                root.get("endAt").alias("endAt"),
                root.get("createAt").alias("createAt"),
                root.get("workflowStatus").alias("workflowStatus"),
                root.get("registrationStartAt").alias("registrationStartAt"),
                root.get("registrationEndAt").alias("registrationEndAt"),
                root.get("brandPublicAt").alias("brandPublicAt"),
                root.get("maxBooths").alias("maxBooths"),
                EventSpecification.registeredBoothCountSubquery(root, cq, cb).alias("registeredBoothCount"));
        // 設定orderBy: 活動創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createAt")));
        // 查詢結果(有設定limit)
        List<Tuple> rows = fetchPage(cq, pageNumber, pageSize);

        // 另外查詢符合條件的總筆數
        long total = eventRepo.count(spec);

        // ----------設定回傳資料----------
        List<AdminEventListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            LocalDateTime startAt = row.get("startAt", LocalDateTime.class);
            LocalDateTime endAt = row.get("endAt", LocalDateTime.class);
            String eventDate = String.format("%s - %s", startAt.format(dateFormatter), endAt.format(dateFormatter));

            EventStatus status = checkEventStatus(
                    row.get("workflowStatus", WorkflowStatus.class),
                    row.get("registrationStartAt", LocalDateTime.class),
                    row.get("registrationEndAt", LocalDateTime.class),
                    row.get("brandPublicAt", LocalDateTime.class),
                    startAt,
                    endAt,
                    row.get("maxBooths", Integer.class),
                    row.get("registeredBoothCount", Long.class).intValue());

            AdminEventListDto dtoItem = new AdminEventListDto(
                    row.get("id", Long.class),
                    row.get("coverImageUrl", String.class),
                    row.get("title", String.class),
                    eventDate,
                    status,
                    row.get("organizerName", String.class),
                    null// TODO:審核時間
            );
            dtoList.add(dtoItem);
        }

        PageResponse<AdminEventListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // 設定管理員後台: 活動詳細
    @Override
    public AdminEventDetailDto getEventDetail(@NonNull Long eventId) {
        // ----------撈資料----------
        AdminEventDetailProjection event = eventRepo.findEventDetailById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        List<EventStallZone> zones = eventStallZoneRepo.findByMarketEventId(eventId);
        int registeredBoothCount = eventRepo.countRegBoothsByEventId(eventId);

        // ----------塞資料----------
        String eventTime = String.format(
                "%s - %s %s-%s",
                event.startAt().format(dateFormatter),
                event.endAt().format(dateFormatter),
                event.startAt().format(timeFormatter),
                event.endAt().format(timeFormatter));
        // 沒有主辦方資料(organizerProfile)時不組營業時間
        String serviceHours = String.format(
                "%s %s-%s",
                event.serviceDays() == null ? "" : event.serviceDays(),
                event.serviceStartTime() == null ? "" : event.serviceStartTime().format(timeFormatter),
                event.serviceEndTime() == null ? "" : event.serviceEndTime().format(timeFormatter));
        String addr = String.format(
                "%s%s%s",
                event.city(),
                event.district() == null ? "" : event.district(),
                event.address());
        String contactAddr = String.format(
                "%s%s%s",
                event.contactCity() == null ? "" : event.contactCity(),
                event.contactDist() == null ? "" : event.contactDist(),
                event.contactAddr() == null ? "" : event.contactAddr());

        String boothSpec = String.format(
                "%d * %d",
                event.stallLength() == null ? 0 : event.stallLength(),
                event.stallWidth() == null ? 0 : event.stallWidth());
        EventStatus eventStatus = checkEventStatus(
                event.workflowStatus(),
                event.regStartAt(),
                event.regEndAt(),
                event.brandPublicAt(),
                event.startAt(),
                event.endAt(),
                event.maxBooths(),
                registeredBoothCount);

        List<BoothZone> boothZones = zones.stream()
                .map(zone -> new BoothZone(zone.getZoneName(), zone.getStallCount()))
                .toList();

        return new AdminEventDetailDto(
                event.eventId(),
                event.coverImg(),
                event.eventName(),
                event.locationName(),
                addr,
                eventStatus,
                event.eventType(),
                event.description(),
                event.regStartAt().format(dateTimeFormatter),
                event.regEndAt().format(dateTimeFormatter),
                event.publicInfoAt() == null ? null : event.publicInfoAt().format(dateTimeFormatter),
                eventTime,
                event.organizerName(),
                event.texId(),
                serviceHours,
                event.contactName(),
                event.contactPhone(),
                event.contactEmail(),
                contactAddr,
                event.mrt(),
                event.bus(),
                event.driving(),
                boothSpec,
                event.maxBooths(),
                event.boothFee(),
                boothZones,
                event.mapImg(),
                null // TODO:活動狀態Logs ->需另外查詢status_logs並組裝
        );
    }

    @Override
    public PageResponse<AdminUserListDto> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        // 只撈頁面需要用到的欄位，避免撈出整張表
        // 設定要join的表
        TupleQueryContext<User> ctx = newTupleQuery(User.class);
        CriteriaBuilder cb = ctx.cb();
        CriteriaQuery<Tuple> cq = ctx.cq();
        Root<User> root = ctx.root();
        Join<User, UserProfile> userProfile = root.join("userProfile");
        // 設定搜尋條件
        Specification<User> spec = UserSpecification.build(request);
        applyPredicate(root, cq, cb, spec);
        // 組裝select欄位
        cq.multiselect(
                root.get("id").alias("id"),
                root.get("role").alias("role"),
                userProfile.get("name").alias("name"),
                root.get("status").alias("status"),
                root.get("email").alias("email"),
                root.get("createdAt").alias("regAt"));
        // 設定orderBy: 帳號創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createdAt")));
        // 查詢結果(有設定limit)
        List<Tuple> rows = fetchPage(cq, pageNumber, pageSize);
        // 另外查詢符合條件的總筆數
        long total = userRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminUserListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            AdminUserListDto dtoItem = new AdminUserListDto(
                    row.get("id", Long.class),
                    row.get("role", Role.class).getRole(),
                    row.get("name", String.class),
                    row.get("status", UserStatus.class).getStatus(),
                    row.get("email", String.class),
                    row.get("regAt", LocalDateTime.class).format(dateTimeFormatter),
                    null);// TODO:另外查詢

            dtoList.add(dtoItem);
        }
        PageResponse<AdminUserListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // for 管理員後台使用者詳細
    @Override
    public AdminVenderDetailDto getVenderDetail(@NonNull Long userId) {
        // TODO: 設定管理員後台: 攤主詳細
        throw new UnsupportedOperationException("Unimplemented method 'setVenderDetail'");
    }

    @Override
    public PageResponse<AdminVenderRegDto> getVenderRegLogs(@Nonnull Long userId, int pageNumber, int pageSize) {
        // TODO 設定管理員後台: 攤主詳細: 活動報名紀錄
        throw new UnsupportedOperationException("Unimplemented method 'getVenderRegLogs'");
    }

    @Override
    public AdminOrgDetailDto getOrganizerDetail(Long userId) {
        // TODO: 設定管理員後台: 主辦方詳細
        throw new UnsupportedOperationException("Unimplemented method 'setOrganizerDetail'");
    }

        @Override
    public PageResponse<AdminOrgEventManagementDto> getOrgEventLogs(Long userId, int pageNumber, int pageSize) {
        // TODO 設定管理員後台: 主辦方詳細 :活動管理紀錄
        throw new UnsupportedOperationException("Unimplemented method 'getOrgEventLogs'");
    }

    @Override
    public PageResponse<AdminUserLoginDto> getUserLoginLogs(Long userId, int pageNumber, int pageSize) {
        // TODO 設定管理員後台: 使用者詳細 :使用者登入紀錄
        throw new UnsupportedOperationException("Unimplemented method 'getUserLoginLogs'");
    }

    @Override
    public PageResponse<AdminOperationLogDto> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        // 只撈頁面需要用到的欄位，避免撈出整張表
        // 設定要join的表
        TupleQueryContext<AdminOperationLog> ctx = newTupleQuery(AdminOperationLog.class);
        CriteriaBuilder cb = ctx.cb();
        CriteriaQuery<Tuple> cq = ctx.cq();
        Root<AdminOperationLog> root = ctx.root();
        Join<AdminOperationLog, User> user = root.join("user");
        // 設定搜尋條件
        Specification<AdminOperationLog> spec = AdminLogSpecification.build(request);
        applyPredicate(root, cq, cb, spec);
        Expression<String> targetEmail = AdminLogSpecification.targetEmailSubquery(root, cq, cb);
        Expression<AdminTargetTypeForFront> targetTypeForFront = AdminLogSpecification
                .targetTypeForFrontExpression(root, cq, cb);
        // 組裝select欄位
        cq.multiselect(
                root.get("operationType").alias("operationType"),
                root.get("targetLabel").alias("targetName"),
                root.get("createdAt").alias("createdAt"),
                root.get("content").alias("content"),
                targetEmail.alias("email"),
                targetTypeForFront.alias("targetType"));
        // 設定orderBy: 操作時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createdAt")));
        // 查詢結果(有設定limit)
        List<Tuple> rows = fetchPage(cq, pageNumber, pageSize);
        // 另外查詢符合條件的總筆數
        long total = logRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminOperationLogDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            AdminOperationLogDto dtoItem = new AdminOperationLogDto(
                    null, // TODO:確認資料庫
                    row.get("operationType", AdminOperationType.class),
                    row.get("targetType", AdminTargetTypeForFront.class),
                    row.get("targetName", String.class),
                    row.get("email", String.class),
                    row.get("createdAt", LocalDateTime.class).format(dateTimeFormatter),
                    row.get("content", String.class));

            dtoList.add(dtoItem);
        }

        PageResponse<AdminOperationLogDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    /**
     * @param data 型別要是:AdminEventItemProjection或是MarketEvent
     * @return 轉換後顯示在前端的活動狀態
     * @throws IllegalArgumentException 型別不符時拋出
     */
    @Override
    public EventStatus changeToEventStatus(Object data) {
        if (data instanceof MarketEvent) {
            MarketEvent entity = (MarketEvent) data;

            return checkEventStatus(
                    entity.getWorkflowStatus(),
                    entity.getRegistrationStartAt(),
                    entity.getRegistrationEndAt(),
                    entity.getBrandPublicAt(),
                    entity.getStartAt(),
                    entity.getEndAt(),
                    entity.getMaxBooths(),
                    entity.getEventApplications() == null ? 0 : entity.getEventApplications().size());
        }
        throw new IllegalArgumentException("data須符合型別類型:AdminEventItemProjection或是MarketEvent");
    }

    /** 封裝一次tuple查詢共用的cb/cq/root，避免每個方法重複建立 */
    private record TupleQueryContext<T>(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<T> root) {
    }

    /** 建立指定entity的tuple查詢context(cb、cq、root) */
    private <T> TupleQueryContext<T> newTupleQuery(Class<T> entityClass) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<T> root = cq.from(entityClass);
        return new TupleQueryContext<>(cb, cq, root);
    }

    /** 套用Specification產生的搜尋條件(若有) */
    private <T> void applyPredicate(Root<T> root, CriteriaQuery<?> cq, CriteriaBuilder cb, Specification<T> spec) {
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
    }

    /** 依分頁參數查詢tuple結果 */
    private List<Tuple> fetchPage(CriteriaQuery<Tuple> cq, int pageNumber, int pageSize) {
        return entityManager.createQuery(cq)
                .setFirstResult((pageNumber - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }



}
