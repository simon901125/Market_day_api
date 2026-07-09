package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.User;
import com.example.demo.enums.EventStatus;
import com.example.demo.enums.Role;
import com.example.demo.enums.WorkflowStatus;
import com.example.demo.projection.admin.AdminEventItemProjection;

@Service
public class AdminService implements AdminServiceInterface, EventStatusServiceInterface {
    @Autowired
    EventRepo eventRepo;

    @Autowired
    UserRepo userRepo;

    @Override
    public AdminDashboardDto setDashboardResponse() {
        LocalDateTime now = LocalDateTime.now();

        //塞資料
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
        Pageable pageable = PageRequest.of(pageNumber, pageSize); //設定分頁
        Page<AdminEventItemProjection> list = eventRepo.findAllByOrderByCreateAtDesc(pageable); // 撈資料

        // 塞資料
        List<AdminEventsItemDto> dtoList = new ArrayList<>();
        for (AdminEventItemProjection item : list) {
            AdminEventsItemDto dtoItem = new AdminEventsItemDto();
            dtoItem.setId(item.getId());
            dtoItem.setImgUrl(item.getImgUrl());
            dtoItem.setName(item.getName());
            dtoItem.setOrganizer(item.getOrganizer());
            dtoItem.setStartDate(item.getStartAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            dtoItem.setEndDate(item.getEndAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            dtoItem.setCreatedAt(item.getCreateAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            dtoItem.setStatus(changeToEventStatus(item).toString());

            dtoList.add(dtoItem);
        }

        return dtoList;
    }

    // 設定管理員後台: 活動搜尋: 搜尋:?
    @Override
    public List<AdminEventsItemDto> setEventsList(AdminEventSearchDto request, int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize); //設定分頁
        Specification<MarketEvent> spec = EventSpecification.build(request); //設定搜尋條件
        Page<MarketEvent> list = eventRepo.findAll(spec, pageable); // 撈資料

        // 塞資料
        List<AdminEventsItemDto> dtoList = new ArrayList<>();
        for (MarketEvent item : list) {
            AdminEventsItemDto dtoItem = new AdminEventsItemDto();
            dtoItem.setId(item.getId());
            dtoItem.setImgUrl(item.getCoverImageUrl());
            dtoItem.setName(item.getTitle());
            dtoItem.setOrganizer(item.getUser().getUserProfile().getName());
            dtoItem.setStartDate(item.getStartAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            dtoItem.setEndDate(item.getEndAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            dtoItem.setCreatedAt(item.getCreateAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            dtoItem.setStatus(changeToEventStatus(item).toString());
            dtoList.add(dtoItem);
        }

        return dtoList;
    }

    @Override
    public AdminEventDetailDto setEventDetail(Long eventId) {
         // 撈資料
        MarketEvent item = eventRepo.findById(eventId).orElse(null);

        // 塞資料
        AdminEventDetailDto dto = new AdminEventDetailDto();
        
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
        throw new IllegalArgumentException("data須符合型別類型:AdminEventItemProjection");
    }

}
