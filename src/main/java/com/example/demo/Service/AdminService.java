package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.admin.AdminEventsItemDto;
import com.example.demo.dto.response.admin.AdminLogsDto;
import com.example.demo.dto.response.admin.AdminOrganizerDetailDto;
import com.example.demo.dto.response.admin.AdminUserItemDto;
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

@Service
public class AdminService implements AdminServiceInterface, EventStatusServiceInterface<Object> {
    @Autowired
    EventRepo eventRepo;

    @Autowired
    UserRepo userRepo;

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    //設定管理員後台: 首頁資料統計部分
    @Override
    public AdminDashboardDto setDashboardResponse() {
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
    public Object setNotice(String bookMark, int pageNumber, int pageSize) {
        // TODO:for 管理員後台通知中心
        throw new UnsupportedOperationException("Unimplemented method 'setNotice'");
    }

    // 設定管理員後台: 活動搜尋
    @Override
    public List<AdminEventsItemDto> setEventsList(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize); // 設定分頁
        Page<AdminEventItemProjection> list = eventRepo.findAllByOrderByCreateAtDesc(pageable); // 撈資料

        // 塞資料
        List<AdminEventsItemDto> dtoList = new ArrayList<>();
        for (AdminEventItemProjection item : list) {
            AdminEventsItemDto dtoItem = new AdminEventsItemDto();
            dtoItem.setId(item.getId());
            dtoItem.setImgUrl(item.getImgUrl());
            dtoItem.setName(item.getName());
            dtoItem.setOrganizer(item.getOrganizer());
            dtoItem.setStartDate(item.getStartAt().format(dateFormatter));
            dtoItem.setEndDate(item.getEndAt().format(dateFormatter));
            dtoItem.setCreatedAt(item.getCreateAt().format(dateTimeFormatter));
            dtoItem.setStatus(changeToEventStatus(item).toString());

            dtoList.add(dtoItem);
        }

        return dtoList;
    }

    // 設定管理員後台: 活動搜尋: 搜尋:?
    @Override
    public List<AdminEventsItemDto> setEventsList(AdminEventSearchDto request, int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize); // 設定分頁
        Specification<MarketEvent> spec = EventSpecification.build(request); // 設定搜尋條件
        Page<MarketEvent> list = eventRepo.findAll(spec, pageable); // 撈資料

        // 塞資料
        List<AdminEventsItemDto> dtoList = new ArrayList<>();
        for (MarketEvent item : list) {
            AdminEventsItemDto dtoItem = new AdminEventsItemDto();
            dtoItem.setId(item.getId());
            dtoItem.setImgUrl(item.getCoverImageUrl());
            dtoItem.setName(item.getTitle());
            dtoItem.setOrganizer(item.getUser().getUserProfile().getName());
            dtoItem.setStartDate(item.getStartAt().format(dateFormatter));
            dtoItem.setEndDate(item.getEndAt().format(dateFormatter));
            dtoItem.setCreatedAt(item.getCreateAt().format(dateTimeFormatter));
            dtoItem.setStatus(changeToEventStatus(item).toString());
            dtoList.add(dtoItem);
        }

        return dtoList;
    }

    // 設定管理員後台: 活動詳細
    @Override
    public AdminEventDetailDto setEventDetail(@NonNull Long eventId) {

        // 撈資料
        MarketEvent event = eventRepo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        UserProfile profile = event.getUser().getUserProfile();
        OrganizerProfile organizerProfile = profile.getOrganizerProfile();

        // 塞資料
        AdminEventDetailDto dto = new AdminEventDetailDto();
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
            organizerProfile.getServiceEndTime().format(timeFormatter)
        );
        dto.setServiceHours(serviceHours);

        //TODO:詢問交通方式欄位
        dto.setMrt(event.getTrafficInfo());
        dto.setBus(null);
        dto.setDrivingDirections(null);

        String boothSpec = String.format("%d * %d", event.getEventStalls().getFirst().getWidth(), event.getEventStalls().getFirst().getLength());
        dto.setBoothSpec(boothSpec);

        dto.setBoothCount(event.getMaxBooths().toString());
        dto.setBoothPrice(event.getBaseFee().toString());

        List<EventStallZone> stallZones = event.getEventStallZones();
        List<BoothZone> dtoBoothZones= new ArrayList<>();
        for (EventStallZone stallZone : stallZones) {
            BoothZone boothZone = new BoothZone();
            boothZone.setName(stallZone.getZoneName());
            boothZone.setQty(stallZone.getStallCount());
            dtoBoothZones.add(boothZone);
        }
        dto.setBoothZones(dtoBoothZones);

        dto.setBoothLayoutImage(event.getMapImageUrl());
        dto.setLogs(null); //TODO:等問清楚系統LOG記錄再做

        return dto;
    }

    // for 管理員後台使用者搜尋
    @Override
    public List<AdminUserItemDto> setUserList(int pageNumber, int pageSize) {
        // TODO: 設定管理員後台: 使用者搜尋
        throw new UnsupportedOperationException("Unimplemented method 'setUserList'");
    }

    @Override
    public List<AdminUserItemDto> setUserList(AdminUserSearchDto request, int pageNumber, int pageSize) {
        // TODO: 設定管理員後台: 使用者搜尋: 搜尋:?
        throw new UnsupportedOperationException("Unimplemented method 'setUserList'");
    }

    // for 管理員後台使用者詳細
    @Override
    public AdminVenderDetailDto setVenderDetail(Long userId) {
        // TODO: 設定管理員後台: 攤主詳細
        throw new UnsupportedOperationException("Unimplemented method 'setVenderDetail'");
    }

    @Override
    public AdminOrganizerDetailDto setOrganizerDetail(Long userId) {
        // TODO: 設定管理員後台: 主辦方詳細
        throw new UnsupportedOperationException("Unimplemented method 'setOrganizerDetail'");
    }

    // for 管理員後台Logs
    @Override
    public AdminLogsDto setLogs(int pageNumber, int pageSize) {
        // TODO: 設定管理員後台: 操作紀錄
        throw new UnsupportedOperationException("Unimplemented method 'setLogs'");
    }

    @Override
    public AdminLogsDto setLogs(AdminLogSearchDto request, int pageNumber, int pageSize) {
        // TODO: 設定管理員後台: 操作紀錄 搜尋:?
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
