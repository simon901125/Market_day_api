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

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventApplicationRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.StatusLogRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminEventDetailProjection;
import com.example.demo.Repository.projection.admin.AdminOrgEventLogProjection;
import com.example.demo.Repository.projection.admin.ApplicationDateProjection;
import com.example.demo.Repository.projection.admin.EventStatusLogProjection;
import com.example.demo.Repository.projection.admin.RefundProjection;
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
import com.example.demo.dto.response.admin.RegBooth;
import com.example.demo.dto.response.admin.StatusLog;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.EventStallZone;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.PaymentStatus;
import com.example.demo.enums.status.RefundStatus;
import com.example.demo.enums.status.ReviewStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetTypeForFront;
import com.example.demo.enums.type.Role;

import jakarta.annotation.Nonnull;
import jakarta.persistence.Tuple;

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

    @Autowired
    EventApplicationRepo eventApplicationRepo;

    @Autowired
    StatusLogRepo statusLogRepo;

    @Autowired
    MessageSource messageSource;

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
        Specification<MarketEvent> spec = EventSpecification.build(request);
        List<Tuple> rows = eventRepo.findEventListTuples(spec, pageNumber, pageSize);

        // 另外查詢符合條件的總筆數
        long total = eventRepo.count(spec);

        // ----------設定回傳資料----------
        List<AdminEventListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            LocalDateTime startAt = row.get("startAt", LocalDateTime.class);
            LocalDateTime endAt = row.get("endAt", LocalDateTime.class);
            String eventDate = String.format("%s - %s", startAt.format(dateFormatter), endAt.format(dateFormatter));
            LocalDateTime submittedAt = row.get("submittedAt", LocalDateTime.class);
            String submittedAtStr = submittedAt == null ? "活動尚未送審" : submittedAt.format(dateTimeFormatter);

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
                event.startAt().format(dateFormatter),
                event.endAt().format(dateFormatter),
                event.startAt().format(timeFormatter),
                event.endAt().format(timeFormatter));
        // 沒有主辦方資料(organizerProfile)時不組營業時間
        String serviceHours = String.format(
                "%s %s-%s",
                event.serviceDays() == null ? "" : event.serviceDays(),
                event.serviceStartTime() == null ? "營業開始時間" : event.serviceStartTime().format(timeFormatter),
                event.serviceEndTime() == null ? "營業結束時間" : event.serviceEndTime().format(timeFormatter));
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
            String loginTimeStr = loginTime == null ? null : loginTime.format(dateTimeFormatter);

            AdminUserListDto dtoItem = new AdminUserListDto(
                    row.get("id", Long.class),
                    row.get("role", Role.class).getRole(),
                    userName,
                    row.get("status", UserStatus.class).getStatus(),
                    row.get("email", String.class),
                    row.get("regAt", LocalDateTime.class).format(dateTimeFormatter),
                    loginTimeStr);

            dtoList.add(dtoItem);
        }
        PageResponse<AdminUserListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // for 管理員後台使用者詳細
    @Override
    public AdminVenderDetailDto getVenderDetail(@NonNull Long userId, int pageSize) {
        // TODO: 設定管理員後台: 攤主詳細
        throw new UnsupportedOperationException("Unimplemented method 'setVenderDetail'");
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
            String regDate = date.applyDate() == null ? "" : date.applyDate().format(dateFormatter);
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

    @Override
    public AdminOrgDetailDto getOrganizerDetail(Long userId, int pageSize) {
        // TODO: 設定管理員後台: 主辦方詳細
        throw new UnsupportedOperationException("Unimplemented method 'setOrganizerDetail'");
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
                    startAt.format(dateFormatter),
                    endAt.format(dateFormatter),
                    startAt.format(timeFormatter),
                    endAt.format(timeFormatter));

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

    @Override
    public PageResponse<AdminUserLoginDto> getUserLoginLogs(Long userId, int pageNumber, int pageSize) {
        // TODO 設定管理員後台: 使用者詳細 :使用者登入紀錄
        throw new UnsupportedOperationException("Unimplemented method 'getUserLoginLogs'");
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
                    row.get("createdAt", LocalDateTime.class).format(dateTimeFormatter),
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

    //處理活動狀態變動紀錄:說明的文字映射
    private StatusLog toStatusLog(EventStatusLogProjection log) {
        String dateTime = log.reqAt() == null ? null : log.reqAt().format(dateTimeFormatter);
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
