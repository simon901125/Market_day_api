package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.admin.AdminEventsItemDto;
import com.example.demo.dto.response.admin.AdminOrganizerDetailDto;
import com.example.demo.dto.response.admin.AdminVenderDetailDto;
import com.example.demo.dto.response.admin.BoothZone;
import com.example.demo.entity.EventStallZone;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.OrganizerProfile;
import com.example.demo.entity.User;
import com.example.demo.entity.UserProfile;
import com.example.demo.enums.EventStatus;
import com.example.demo.enums.Role;
import com.example.demo.enums.WorkflowStatus;
import com.example.demo.projection.admin.AdminEventItemProjection;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class AdminService implements AdminServiceInterface, EventStatusServiceInterface<Object> {
    @Autowired
    EventRepo eventRepo;

    @Autowired
    UserRepo userRepo;

    @PersistenceContext
    EntityManager entityManager;

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
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
        dto.setTotalOrganizer(userRepo.countByRoleAndStatus(Role.ORGANIZER, User.Status.ACTIVE));
        dto.setTotalVender(userRepo.countByRoleAndStatus(Role.VENDOR, User.Status.ACTIVE));
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
        //----------只撈頁面需要用到的欄位，避免撈出整張表----------
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

        //組裝select欄位
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
        //設定orderBy: 活動創建時間:由新到舊(desc)
        cq.orderBy(cb.desc(root.get("createAt")));
        //查詢結果(有設定limit)
        List<Tuple> rows = entityManager.createQuery(cq)
                .setFirstResult((pageNumber - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        // 另外查詢符合條件的總筆數
        long total = eventRepo.count(spec);

        //----------設定回傳資料----------
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
        //----------撈資料----------

        //TODO:定義projection
        MarketEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        UserProfile profile = event.getUser().getUserProfile();
        OrganizerProfile organizerProfile = profile.getOrganizerProfile();
        //----------塞資料----------
        AdminEventDetailDto dto = new AdminEventDetailDto();
        //活動基本資料:活動名稱、活動類型
        dto.setEventName(event.getTitle());
        dto.setEventType(changeToEventStatus(event).getDescription());

        String eventTime = String.format(
                "%s ~ %s %s~%s",
                event.getStartAt().format(dateFormatter),
                event.getEndAt().format(dateFormatter),
                event.getStartAt().format(timeFormatter),
                event.getEndAt().format(timeFormatter));
        dto.setEventTime(eventTime);

        dto.setLocationName(event.getLocationName());
        dto.setAddr(String.format("%s%s%s", event.getCity(), event.getDistrict(), event.getAddress()));

        dto.setDescription(event.getDescription());
        dto.setRegistrationStartTime(event.getRegistrationStartAt().format(dateTimeFormatter));
        dto.setRegistrationEndTime(event.getRegistrationEndAt().format(dateTimeFormatter));
        dto.setFinalListConfirmation(
                event.getBrandPublicAt() == null ? event.getBrandPublicAt().format(dateTimeFormatter) : "-");
        dto.setActivityTime(eventTime);
        dto.setOrganizerName(organizerProfile.getCompanyName());
        dto.setContactPerson(profile.getContactName());
        dto.setContactPhone(profile.getContactPhone());
        dto.setEmail(profile.getContactEmail());
        dto.setAddress(profile.getAddress());
        dto.setTaxId(organizerProfile.getTaxId());

        String serviceHours = String.format(
                "%s %s~$s",
                organizerProfile.getServiceDays(),
                organizerProfile.getServiceStartTime().format(timeFormatter),
                organizerProfile.getServiceEndTime().format(timeFormatter));
        dto.setServiceHours(serviceHours);

        // TODO:詢問交通方式欄位
        dto.setMrt(event.getTrafficInfo());
        dto.setBus(null);
        dto.setDrivingDirections(null);

        String boothSpec = String.format("%d * %d", event.getEventStalls().getFirst().getWidth(),
                event.getEventStalls().getFirst().getLength());
        dto.setBoothSpec(boothSpec);

        dto.setBoothCount(event.getMaxBooths().toString());
        dto.setBoothPrice(event.getBaseFee().toString());

        List<EventStallZone> stallZones = event.getEventStallZones();
        List<BoothZone> dtoBoothZones = new ArrayList<>();
        for (EventStallZone stallZone : stallZones) {
            BoothZone boothZone = new BoothZone();
            boothZone.setName(stallZone.getZoneName());
            boothZone.setQty(stallZone.getStallCount());
            dtoBoothZones.add(boothZone);
        }
        dto.setBoothZones(dtoBoothZones);

        dto.setBoothLayoutImage(event.getMapImageUrl());
        dto.setLogs(null); // TODO:等問清楚系統LOG記錄再做

        return dto;
    }

    @Override
    public PageResponse<?> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize) {
        // TODO: 設定管理員後台: 使用者搜尋
        throw new UnsupportedOperationException("Unimplemented method 'setUserList'");
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
        // TODO: 設定管理員後台: 操作紀錄
        throw new UnsupportedOperationException("Unimplemented method 'setLogs'");
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
