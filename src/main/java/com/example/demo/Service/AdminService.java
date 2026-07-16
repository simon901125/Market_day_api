package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventApplicationRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.EventUnpublishRequestRepo;
import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.RequestLogRepo;
import com.example.demo.Repository.StatusLogRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminEventDetailProjection;
import com.example.demo.Repository.projection.admin.AdminLookupProjection;
import com.example.demo.Repository.projection.admin.AdminOrgEventLogProjection;
import com.example.demo.Repository.projection.admin.AdminOrganizerDetailProjection;
import com.example.demo.Repository.projection.admin.AdminVenderDetailProjection;
import com.example.demo.Repository.projection.admin.ApplicationDateProjection;
import com.example.demo.Repository.projection.admin.EventApprovalProjection;
import com.example.demo.Repository.projection.admin.EventStatusLogProjection;
import com.example.demo.Repository.projection.admin.RefundProjection;
import com.example.demo.Repository.projection.admin.UserAccountStatusProjection;
import com.example.demo.Repository.projection.admin.UserLoginLogProjection;
import com.example.demo.Repository.projection.admin.VenderRegApplicationProjection;
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
import com.example.demo.dto.response.admin.EventStatusChangeDto;
import com.example.demo.dto.response.admin.RegBooth;
import com.example.demo.dto.response.admin.StatusLog;
import com.example.demo.dto.response.admin.UserStatusChangeDto;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.EventStallZone;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.PaymentStatus;
import com.example.demo.enums.status.RefundStatus;
import com.example.demo.enums.status.ReviewStatus;
import com.example.demo.enums.status.UnpublishRequestStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetType;
import com.example.demo.enums.type.AdminTargetTypeForFront;
import com.example.demo.enums.type.NotificationCategory;
import com.example.demo.enums.type.NotificationTargetType;
import com.example.demo.enums.type.Role;

import jakarta.annotation.Nonnull;
import jakarta.persistence.Tuple;

@Service
public class AdminService extends AdminServiceBase implements EventStatusServiceInterface<Object> {
    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventStallZoneRepo eventStallZoneRepo;

    @Autowired
    UserRepo userRepo;

    @Autowired
    AdminLogRepo logRepo;

    @Autowired
    EventApplicationRepo eventApplicationRepo;

    @Autowired
    RequestLogRepo requestLogRepo;

    @Autowired
    StatusLogRepo statusLogRepo;

    @Autowired
    NotificationRepo notificationRepo;

    @Autowired
    EventUnpublishRequestRepo eventUnpublishRequestRepo;

    @Autowired
    MessageSource messageSource;

    /** yyyy/MM/dd HH:mm */
    private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    /** yyyy/MM/dd */
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /** HH:mm */
    private final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 主辦方登入API路徑 */
    private static final List<String> ORGANIZER_LOGIN_PATHS = List.of(
            "/api/organizer/google-login",
            "/api/organizer/local-login");
    /** 攤主登入API路徑 */
    private static final List<String> VENDOR_LOGIN_PATHS = List.of(
            "/api/vender/google-login",
            "/api/vender/local-login");

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
        Specification<MarketEvent> spec = EventSpecification.build(request);
        List<Tuple> rows = eventRepo.findEventListTuples(spec, pageNumber, pageSize);

        // 另外查詢符合條件的總筆數
        long total = eventRepo.count(spec);

        // ----------設定回傳資料----------
        List<AdminEventListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            LocalDateTime startAt = row.get("startAt", LocalDateTime.class);
            LocalDateTime endAt = row.get("endAt", LocalDateTime.class);
            String eventDate = String.format(
                    "%s - %s",
                    startAt == null ? "" : startAt.format(DATE_FORMATTER),
                    endAt == null ? "" : endAt.format(DATE_FORMATTER));
            LocalDateTime submittedAt = row.get("submittedAt", LocalDateTime.class);
            String submittedAtStr = submittedAt == null ? "活動尚未送審" : submittedAt.format(DATE_TIME_FORMATTER);

            WorkflowStatus workflowStatus = row.get("workflowStatus", WorkflowStatus.class);
            Integer maxBooths = row.get("maxBooths", Integer.class);
            Long registeredBoothCount = row.get("registeredBoothCount", Long.class);
            EventStatus status = workflowStatus == null
                    ? null
                    : checkEventStatus(
                            workflowStatus,
                            row.get("registrationStartAt", LocalDateTime.class),
                            row.get("registrationEndAt", LocalDateTime.class),
                            row.get("brandPublicAt", LocalDateTime.class),
                            startAt,
                            endAt,
                            maxBooths == null ? 0 : maxBooths,
                            registeredBoothCount == null ? 0 : registeredBoothCount.intValue());

            AdminEventListDto dtoItem = new AdminEventListDto(
                    row.get("id", Long.class),
                    row.get("coverImageUrl", String.class),
                    row.get("title", String.class),
                    eventDate,
                    status,
                    row.get("organizerName", String.class),
                    submittedAtStr);

            dtoList.add(dtoItem);
        }

        PageResponse<AdminEventListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // 設定管理員後台: 活動詳細
    @Override
    public AdminEventDetailDto getEventDetail(@NonNull Long eventId, int pageSize) throws IllegalArgumentException {
        // ----------撈資料----------
        AdminEventDetailProjection event = eventRepo.findEventDetailById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        List<EventStallZone> zones = eventStallZoneRepo.findByMarketEventId(eventId);
        int registeredBoothCount = eventApplicationRepo.countRegBoothsByEventId(eventId);

        // ----------塞資料----------
        String eventTime = String.format(
                "%s - %s %s-%s",
                event.startAt().format(DATE_FORMATTER),
                event.endAt().format(DATE_FORMATTER),
                event.startAt().format(TIME_FORMATTER),
                event.endAt().format(TIME_FORMATTER));
        // 沒有主辦方資料(organizerProfile)時不組營業時間
        String serviceHours = String.format(
                "%s %s-%s",
                event.serviceDays() == null ? "" : event.serviceDays(),
                event.serviceStartTime() == null ? "營業開始時間" : event.serviceStartTime().format(TIME_FORMATTER),
                event.serviceEndTime() == null ? "營業結束時間" : event.serviceEndTime().format(TIME_FORMATTER));
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
                event.regStartAt().format(DATE_TIME_FORMATTER),
                event.regEndAt().format(DATE_TIME_FORMATTER),
                event.publicInfoAt() == null ? null : event.publicInfoAt().format(DATE_TIME_FORMATTER),
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
                getEventStatusLogs(eventId, 1, pageSize)
        );
    }

    // 設定管理員後台: 活動詳細:活動狀態變動紀錄
    @Override
    public PageResponse<StatusLog> getEventStatusLogs(@NonNull Long eventId, int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<EventStatusLogProjection> logs = statusLogRepo.findEventStatusLogs(eventId, pageRequest);
        long total = statusLogRepo.countEventStatusLogs(eventId);

        List<StatusLog> items = logs.stream()
                .map(this::toStatusLog)
                .toList();

        return new PageResponse<StatusLog>(items, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 使用者搜尋
    @Override
    public PageResponse<AdminUserListDto> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        Specification<User> spec = UserSpecification.build(request);
        List<Tuple> rows = userRepo.findUserListTuples(spec, pageNumber, pageSize);
        // 另外查詢符合條件的總筆數
        long total = userRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminUserListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            // null處理
            LocalDateTime loginTime = row.get("loginTime", LocalDateTime.class);
            String userName = row.get("name", String.class) == null ? "使用者尚未填寫" : row.get("name", String.class);
            String loginTimeStr = loginTime == null ? null : loginTime.format(DATE_TIME_FORMATTER);

            AdminUserListDto dtoItem = new AdminUserListDto(
                    row.get("id", Long.class),
                    row.get("role", Role.class).getRole(),
                    userName,
                    row.get("status", UserStatus.class).getStatus(),
                    row.get("email", String.class),
                    row.get("regAt", LocalDateTime.class).format(DATE_TIME_FORMATTER),
                    loginTimeStr);

            dtoList.add(dtoItem);
        }
        PageResponse<AdminUserListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // for 管理員後台使用者詳細
    @Override
    public AdminVenderDetailDto getVenderDetail(@NonNull Long userId, int pageSize) {
        // ----------撈資料----------
        AdminVenderDetailProjection profile = userRepo.findVenderDetailById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的攤主"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastLoginAt = requestLogRepo.findLastLoginAt(userId, VENDOR_LOGIN_PATHS);
        int ongoingEventCount = eventApplicationRepo.countOngoingEvents(userId, now);
        int endedEventCount = eventApplicationRepo.countEndedEvents(userId, now);

        PageResponse<AdminVenderRegDto> eventRegLogs = getVenderRegLogs(userId, 1, pageSize);
        PageResponse<AdminUserLoginDto> loginLogs = getUserLoginLogs(userId, 1, pageSize);

        // ----------塞資料----------
        String userName = profile.userName() == null ? "使用者尚未填寫" : profile.userName();
        boolean isGoogleBound = profile.provider() != User.Provider.LOCAL;
        String contactAddress = String.format(
                "%s%s%s",
                profile.city() == null ? "" : profile.city(),
                profile.district() == null ? "" : profile.district(),
                profile.address() == null ? "" : profile.address());

        return new AdminVenderDetailDto(
                profile.userId(),
                userName,
                profile.role().getRole(),
                profile.accountStatus().getStatus(),
                isGoogleBound,
                profile.regAt().format(DATE_TIME_FORMATTER),
                lastLoginAt == null ? null : lastLoginAt.format(DATE_TIME_FORMATTER),
                ongoingEventCount,
                endedEventCount,
                profile.brandName(),
                profile.brandType(),
                userName,
                profile.contactPhone(),
                profile.contactEmail(),
                contactAddress,
                eventRegLogs,
                loginLogs);
    }

    // 設定管理員後台: 攤主詳細: 活動報名紀錄
    @Override
    public PageResponse<AdminVenderRegDto> getVenderRegLogs(@Nonnull Long userId, int pageNumber, int pageSize) {
        // ----------撈資料----------
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<VenderRegApplicationProjection> applications = eventApplicationRepo.findVenderRegApplications(userId,
                pageRequest);
        long total = eventApplicationRepo.countByUserId(userId);

        List<Long> applicationIds = applications.stream()
                .map(VenderRegApplicationProjection::applicationId)
                .toList();

        // 依報名編號查詢報名攤位(參與日期+已選定攤位)與退款紀錄
        List<ApplicationDateProjection> dates = eventApplicationRepo.findApplicationDates(applicationIds);
        List<RefundProjection> refunds = eventApplicationRepo.findRefunds(applicationIds);

        // ----------依報名編號分組: 報名攤位----------
        Map<Long, List<RegBooth>> regBoothsByApplication = new HashMap<>();
        for (ApplicationDateProjection date : dates) {
            String regDate = date.applyDate() == null ? "" : date.applyDate().format(DATE_FORMATTER);
            String boothNo = (date.stallNo() == null || date.zoneName() == null)
                    ? "尚未選攤位"
                    : date.zoneName() + date.stallNo();

            regBoothsByApplication
                    .computeIfAbsent(date.applicationId(), key -> new ArrayList<>())
                    .add(new RegBooth(regDate, boothNo));
        }

        // ----------依報名編號分組: 是否已有退款(以及是否已完成退款)----------
        Map<Long, Boolean> hasRefundByApplication = new HashMap<>();
        Map<Long, Boolean> hasRefundedAtByApplication = new HashMap<>();
        for (RefundProjection refund : refunds) {
            hasRefundByApplication.put(refund.applicationId(), true);
            if (refund.refundedAt() != null) {
                hasRefundedAtByApplication.put(refund.applicationId(), true);
            }
        }

        // ----------設定回傳資料----------
        List<AdminVenderRegDto> dtoList = new ArrayList<>();
        for (VenderRegApplicationProjection application : applications) {
            String regStatus = resolveRegStatus(
                    application.isCancelled(), application.reviewStatus(), application.paymentStatus());
            String paymentStatusStr = resolvePaymentStatus(
                    application.applicationId(), application.paymentStatus(),
                    hasRefundByApplication, hasRefundedAtByApplication);
            List<RegBooth> regBooths = regBoothsByApplication.getOrDefault(application.applicationId(), List.of());

            dtoList.add(new AdminVenderRegDto(application.eventName(), regStatus, paymentStatusStr, regBooths));
        }

        return new PageResponse<>(dtoList, pageNumber, pageSize, total);
    }

    //設定管理員後台: 主辦方詳細
    @Override
    public AdminOrgDetailDto getOrganizerDetail(Long userId, int pageSize) {
        // ----------撈資料----------
        AdminOrganizerDetailProjection profile = userRepo.findOrganizerDetailById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的主辦方"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastLoginAt = requestLogRepo.findLastLoginAt(userId, ORGANIZER_LOGIN_PATHS);
        int createdEventCount = eventRepo.countCreatedEventsByUserId(userId);
        int ongoingEventCount = eventRepo.countOngoingEventsByUserId(userId, now);
        int endedEventCount = eventRepo.countEndedEventsByUserId(userId, now);

        PageResponse<AdminOrgEventManagementDto> eventLogs = getOrgEventLogs(userId, 1, pageSize);
        PageResponse<AdminUserLoginDto> loginLogs = getUserLoginLogs(userId, 1, pageSize);

        // ----------塞資料----------
        String userName = profile.userName() == null ? "使用者尚未填寫" : profile.userName();
        boolean isGoogleBound = profile.provider() != User.Provider.LOCAL;
        String serviceHours = String.format(
                "%s %s-%s",
                profile.serviceDays() == null ? "" : profile.serviceDays(),
                profile.serviceStartTime() == null ? "營業開始時間" : profile.serviceStartTime().format(TIME_FORMATTER),
                profile.serviceEndTime() == null ? "營業結束時間" : profile.serviceEndTime().format(TIME_FORMATTER));
        String contactAddress = String.format(
                "%s%s%s",
                profile.city() == null ? "" : profile.city(),
                profile.district() == null ? "" : profile.district(),
                profile.address() == null ? "" : profile.address());

        return new AdminOrgDetailDto(
                profile.userId(),
                userName,
                profile.role().getRole(),
                profile.accountStatus().getStatus(),
                isGoogleBound,
                profile.regAt().format(DATE_TIME_FORMATTER),
                lastLoginAt == null ? null : lastLoginAt.format(DATE_TIME_FORMATTER),
                createdEventCount,
                ongoingEventCount,
                endedEventCount,
                profile.organizerName(),
                serviceHours,
                profile.companyName(),
                userName,
                profile.contactPhone(),
                profile.contactEmail(),
                contactAddress,
                profile.taxId(),
                eventLogs,
                loginLogs);
    }

    // 設定管理員後台: 主辦方詳細: 活動管理紀錄
    @Override
    public PageResponse<AdminOrgEventManagementDto> getOrgEventLogs(@Nonnull Long userId, int pageNumber,
            int pageSize) {
        // ----------撈資料----------
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<AdminOrgEventLogProjection> events = eventRepo.findOrgEventLogs(userId, pageRequest);
        long total = eventRepo.countByUserId(userId);

        // ----------設定回傳資料----------
        List<AdminOrgEventManagementDto> dtoList = new ArrayList<>();
        for (AdminOrgEventLogProjection event : events) {
            LocalDateTime startAt = event.startAt();
            LocalDateTime endAt = event.endAt();
            String eventDate = String.format(
                    "%s - %s %s-%s",
                    startAt.format(DATE_FORMATTER),
                    endAt.format(DATE_FORMATTER),
                    startAt.format(TIME_FORMATTER),
                    endAt.format(TIME_FORMATTER));

            int maxBooths = event.maxBooths() == null ? 0 : event.maxBooths();
            int registeredBoothCount = eventApplicationRepo.countRegBoothsByEventId(event.eventId());
            String registrationCount = String.format("%d/%d", registeredBoothCount, maxBooths);

            WorkflowStatus workflowStatus = event.workflowStatus();
            EventStatus status = workflowStatus == null
                    ? null
                    : checkEventStatus(
                            workflowStatus,
                            event.registrationStartAt(),
                            event.registrationEndAt(),
                            event.brandPublicAt(),
                            startAt,
                            endAt,
                            maxBooths,
                            registeredBoothCount);
            String eventStatus = status != null
                    ? status.getStatus()
                    : (workflowStatus == null ? "" : workflowStatus.getDescription());

            String eventName = event.title() == null ? "" : event.title();

            dtoList.add(new AdminOrgEventManagementDto(eventName, eventDate, eventStatus, registrationCount));
        }

        return new PageResponse<>(dtoList, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 使用者詳細 :使用者登入紀錄
    @Override
    public PageResponse<AdminUserLoginDto> getUserLoginLogs(Long userId, int pageNumber, int pageSize) throws IllegalArgumentException{
        // ----------撈資料----------
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的使用者"));

        // 管理員沒有登入紀錄頁面
        if (user.getRole() == Role.ADMIN) {
            return null;
        }

        List<String> loginPaths = user.getRole() == Role.ORGANIZER ? ORGANIZER_LOGIN_PATHS : VENDOR_LOGIN_PATHS;

        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<UserLoginLogProjection> logs = requestLogRepo.findUserLoginLogs(userId, loginPaths, pageRequest);
        long total = requestLogRepo.countUserLoginLogs(userId, loginPaths);

        // ----------設定回傳資料----------
        List<AdminUserLoginDto> dtoList = new ArrayList<>();
        for (UserLoginLogProjection log : logs) {
            String loginMethod = log.path().contains("google") ? "google" : "Email";
            String loginStatus = log.statusCode() != null && log.statusCode() == 200 ? "成功" : "失敗";

            dtoList.add(new AdminUserLoginDto(
                    log.loginTime() == null ? null : log.loginTime().format(DATE_TIME_FORMATTER),
                    loginMethod,
                    loginStatus));
        }

        return new PageResponse<>(dtoList, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 操作紀錄搜尋
    @Override
    public PageResponse<AdminOperationLogDto> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        // 只撈頁面需要用到的欄位，避免撈出整張表
        Specification<AdminOperationLog> spec = AdminLogSpecification.build(request);
        List<Tuple> rows = logRepo.findLogListTuples(spec, pageNumber, pageSize);
        // 另外查詢符合條件的總筆數
        long total = logRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminOperationLogDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            AdminOperationLogDto dtoItem = new AdminOperationLogDto(
                    row.get("adminName", String.class),
                    row.get("operationType", AdminOperationType.class),
                    row.get("targetType", AdminTargetTypeForFront.class),
                    row.get("targetName", String.class),
                    row.get("email", String.class),
                    row.get("createdAt", LocalDateTime.class).format(DATE_TIME_FORMATTER),
                    row.get("content", String.class));

            dtoList.add(dtoItem);
        }

        PageResponse<AdminOperationLogDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    /**
     * 將MarketEvent轉換成EventStatus
     * 
     * @param data :MarketEvent
     * @return EventStatus
     * @throws IllegalArgumentException 型別不符時拋出
     */
    @Override
    public EventStatus changeToEventStatus(Object data) throws IllegalArgumentException {
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
        throw new IllegalArgumentException("data須符合型別類型MarketEvent");
    }
  
    @Override
    @Transactional
    public UserStatusChangeDto setUserAccountDisable(Long userId, String operatorEmail, Role operatorRole) {
        if (userId == null) {
            throw new IllegalArgumentException("請提供使用者id");
        }
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }

        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        UserAccountStatusProjection target = userRepo.findAccountStatusById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的使用者"));

        UserStatus newStatus = target.status();
        if (target.status() == UserStatus.ACTIVE) {
            userRepo.updateStatusIfCurrent(userId, UserStatus.ACTIVE, UserStatus.DISABLED);
            newStatus = UserStatus.DISABLED;
        }

        String targetLabel = target.contactName() != null ? target.contactName() : target.email();

        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(userRepo.getReferenceById(admin.id()));
        adminLog.setOperationType(AdminOperationType.ACCOUNT_DISABLED);
        adminLog.setTargetType(AdminTargetType.USER);
        adminLog.setTargetId(userId);
        adminLog.setTargetLabel(targetLabel);
        adminLog.setContent(admin.adminName() + "停用" + targetLabel + "的帳號");
        logRepo.save(adminLog);

        return new UserStatusChangeDto(target.contactName(), target.email(), newStatus);
    }

    @Override
    @Transactional
    public UserStatusChangeDto setUserAccountRestore(Long userId, String operatorEmail, Role operatorRole) {
        if (userId == null) {
            throw new IllegalArgumentException("請提供使用者id");
        }
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }

        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        UserAccountStatusProjection target = userRepo.findAccountStatusById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的使用者"));

        UserStatus newStatus = target.status();
        if (target.status() == UserStatus.DISABLED) {
            userRepo.updateStatusIfCurrent(userId, UserStatus.DISABLED, UserStatus.ACTIVE);
            newStatus = UserStatus.ACTIVE;
        }

        String targetLabel = target.contactName() != null ? target.contactName() : target.email();

        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(userRepo.getReferenceById(admin.id()));
        adminLog.setOperationType(AdminOperationType.ACCOUNT_RESTORED);
        adminLog.setTargetType(AdminTargetType.USER);
        adminLog.setTargetId(userId);
        adminLog.setTargetLabel(targetLabel);
        adminLog.setContent(admin.adminName() + "恢復" + targetLabel + "的帳號");
        logRepo.save(adminLog);

        return new UserStatusChangeDto(target.contactName(), target.email(), newStatus);
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventApprove(Long eventId, String operatorEmail, Role operatorRole) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }

        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        eventRepo.updateWorkflowStatusIfCurrent(eventId, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING);

        Notification notification = new Notification();
        notification.setUser(userRepo.getReferenceById(event.organizerId()));
        notification.setCategory(NotificationCategory.EVENT_CHANGE);
        notification.setType("Event_Approve");
        notification.setTargetType(NotificationTargetType.MARKET_EVENT);
        notification.setTargetId(eventId);
        notification.setTitle("審核通過");
        notification.setContent(event.title() + "審核通過，開始建置攤位地圖");
        notificationRepo.save(notification);

        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(userRepo.getReferenceById(admin.id()));
        adminLog.setOperationType(AdminOperationType.ACTIVITY_REVIEW);
        adminLog.setTargetType(AdminTargetType.MARKET_EVENT);
        adminLog.setTargetId(eventId);
        adminLog.setTargetLabel(event.title());
        adminLog.setContent(admin.adminName() + "同意" + event.title() + "申請");
        logRepo.save(adminLog);

        return new EventStatusChangeDto(event.title(), EventStatus.MAP_BUILDING);
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventRevision(Long eventId, String operatorEmail, Role operatorRole, String note) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }

        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("請提供補件原因");
        }

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        eventRepo.updateWorkflowStatusAndReviewNoteIfCurrent(
                eventId, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.REVISION_REQUIRED, note);

        Notification notification = new Notification();
        notification.setUser(userRepo.getReferenceById(event.organizerId()));
        notification.setCategory(NotificationCategory.EVENT_CHANGE);
        notification.setType("Event_Revision");
        notification.setTargetType(NotificationTargetType.MARKET_EVENT);
        notification.setTargetId(eventId);
        notification.setTitle("補件通知");
        notification.setContent(event.title() + "需要補件，請修改後重新送出審核");
        notificationRepo.save(notification);

        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(userRepo.getReferenceById(admin.id()));
        adminLog.setOperationType(AdminOperationType.REQUEST_REVISION);
        adminLog.setTargetType(AdminTargetType.MARKET_EVENT);
        adminLog.setTargetId(eventId);
        adminLog.setTargetLabel(event.title());
        adminLog.setContent(admin.adminName() + "退回" + event.title() + "申請, 原因:" + note);
        logRepo.save(adminLog);

        return new EventStatusChangeDto(event.title(), EventStatus.REVISION_REQUIRED);
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventMapComplete(Long eventId, String operatorEmail, Role operatorRole) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }

        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.MAP_BUILDING) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        eventRepo.updateWorkflowStatusIfCurrent(eventId, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH);

        Notification notification = new Notification();
        notification.setUser(userRepo.getReferenceById(event.organizerId()));
        notification.setCategory(NotificationCategory.EVENT_CHANGE);
        notification.setType("Event_Map_Complete");
        notification.setTargetType(NotificationTargetType.MARKET_EVENT);
        notification.setTargetId(eventId);
        notification.setTitle("地圖完成");
        notification.setContent(event.title() + "攤位地圖已建置完成，可前往活動詳情確認");
        notificationRepo.save(notification);

        String organizerLabel = event.organizerContactName() != null ? event.organizerContactName() : "主辦方";
        String eventTitleLabel = event.title() != null ? event.title() : "活動";

        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(userRepo.getReferenceById(admin.id()));
        adminLog.setOperationType(AdminOperationType.MAP_BUILD_COMPLETED);
        adminLog.setTargetType(AdminTargetType.MARKET_EVENT);
        adminLog.setTargetId(eventId);
        adminLog.setTargetLabel(event.title());
        adminLog.setContent(admin.adminName() + "通知主辦方" + organizerLabel + " " + eventTitleLabel + "地圖建置完成");
        logRepo.save(adminLog);

        return new EventStatusChangeDto(event.title(), EventStatus.READY_TO_PUBLISH);
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventUnpublish(Long eventId, String operatorEmail, Role operatorRole, String note) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }

        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.UNPUBLISH_REQUESTED) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        User adminRef = userRepo.getReferenceById(admin.id());

        Long unpublishRequestId = eventUnpublishRequestRepo
                .findLatestRequestIdByEventIdAndStatus(eventId, UnpublishRequestStatus.PENDING)
                .orElse(null);

        if (unpublishRequestId == null) {
            Notification exceptionNotification = new Notification();
            exceptionNotification.setUser(adminRef);
            exceptionNotification.setCategory(NotificationCategory.EXCEPTION);
            exceptionNotification.setType("System_EXCEPTION");
            exceptionNotification.setTargetType(NotificationTargetType.MARKET_EVENT);
            exceptionNotification.setTargetId(eventId);
            exceptionNotification.setTitle("活動狀態異常");
            exceptionNotification.setContent(event.title() + "活動狀態為申請下架，資料庫查無該活動下架申請單");
            notificationRepo.save(exceptionNotification);

            throw new IllegalArgumentException("找不到該活動的下架申請");
        }

        eventRepo.updateWorkflowStatusIfCurrent(eventId, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.UNPUBLISHED);

        eventUnpublishRequestRepo.reviewIfCurrent(
                unpublishRequestId, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.APPROVED, note);

        Notification notification = new Notification();
        notification.setUser(userRepo.getReferenceById(event.organizerId()));
        notification.setCategory(NotificationCategory.EVENT_CHANGE);
        notification.setType("Event_Unpublish");
        notification.setTargetType(NotificationTargetType.MARKET_EVENT);
        notification.setTargetId(eventId);
        notification.setTitle("活動下架");
        notification.setContent(event.title() + "活動已下架");
        notificationRepo.save(notification);

        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(adminRef);
        adminLog.setOperationType(AdminOperationType.EVENT_UNPUBLISH_REVIEW);
        adminLog.setTargetType(AdminTargetType.EVENT_UNPUBLISH_REQUEST);
        adminLog.setTargetId(unpublishRequestId);
        adminLog.setTargetLabel(event.title());
        adminLog.setContent(admin.adminName() + "審核通過" + event.title() + "活動下架申請");
        logRepo.save(adminLog);

        return new EventStatusChangeDto(event.title(), EventStatus.UNPUBLISHED);
    }

    //處理活動狀態變動紀錄:說明的文字映射
    private StatusLog toStatusLog(EventStatusLogProjection log) {
        String dateTime = log.reqAt() == null ? null : log.reqAt().format(DATE_TIME_FORMATTER);
        WorkflowStatus status = log.newStatus() == null ? null : WorkflowStatus.valueOf(log.newStatus());
        String description = log.newStatus() == null
                ? null
                : messageSource.getMessage("workflow-status." + log.newStatus(), null, Locale.TAIWAN);
        String operator = log.role() == Role.ADMIN ? log.adminName() : log.orgName();

        return new StatusLog(dateTime, status, description, operator);
    }

    /** 依審核狀態、付款狀態、是否取消，轉換為前端顯示的報名狀態文字 */
    private String resolveRegStatus(Boolean isCancelled, ReviewStatus reviewStatus, PaymentStatus paymentStatus) {
        if (Boolean.TRUE.equals(isCancelled)) {
            return "已取消";
        }
        if (reviewStatus == null) {
            return "";
        }
        switch (reviewStatus) {
            case PENDING:
                return "待審核";
            case APPROVED:
                if (paymentStatus == PaymentStatus.PENDING || paymentStatus == PaymentStatus.FAILED) {
                    return "待付款";
                }
                if (paymentStatus == PaymentStatus.PAID) {
                    return "報名完成";
                }
                if (paymentStatus == PaymentStatus.EXPIRED) {
                    return "報名失敗";
                }
                return "";
            case REJECTED:
                return "報名失敗";
            default:
                return "";
        }
    }

    /** 若該報名有退款紀錄，依是否已有退款完成時間判斷為已退款/退款中，否則沿用原本的付款狀態 */
    private String resolvePaymentStatus(
            Long applicationId,
            PaymentStatus paymentStatus,
            Map<Long, Boolean> hasRefundByApplication,
            Map<Long, Boolean> hasRefundedAtByApplication) {
        boolean hasRefund = hasRefundByApplication.getOrDefault(applicationId, false);
        if (hasRefund) {
            boolean hasRefundedAt = hasRefundedAtByApplication.getOrDefault(applicationId, false);
            return hasRefundedAt ? RefundStatus.REFUNDED.getStatus() : RefundStatus.REFUNDING.getStatus();
        }
        return paymentStatus == null ? null : paymentStatus.getStatus();
    }
}
