package com.example.demo.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.AdminService;
import com.example.demo.Service.AdminServiceInterface;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminEventSearchRequest;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理員API", description = "提供與管理員活動審核、使用者帳號停復用相關功能")
public class AdminController {
    final AdminServiceInterface service;

    AdminController(AdminService service) {
        this.service = service;
    }

    /**
     * 用來獲取管理員後台: 首頁資料統計部分<br>
     * <b>API路徑</b>: /api/admin/dashboard/overview<br>
     * @return ApiResponse<AdminDashboardDto>
     */
    @GetMapping("/dashboard/overview")
    public ApiResponse<AdminDashboardDto> getDashboardOverview() {
        AdminDashboardDto response = service.getDashboardResponse();
        return ApiResponse.success("ok", response);
    }

    @PostMapping("/notices/search")
    public ApiResponse<?> getNotices(@RequestBody Map<String, Object> data) {
        // TODO:取得通知列表

        return ApiResponse.success("ok");
    }

    /**
     * 用來獲取管理員後台: 活動搜尋頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/events/search<br>
     * @param request 搜尋條件與分頁參數，pageNumber從1開始計算
     * @return ApiResponse<T>
     */
    @Schema(description = "獲取管理員後台: 活動搜尋頁面所需資料")
    @PostMapping("/events/search")
    public ApiResponse<?> getEventList(@RequestBody AdminEventSearchRequest request) {
        AdminEventSearchDto data = new AdminEventSearchDto(
            request.keyword(), 
            request.organizer(), 
            request.status(), 
            request.startDate(), 
            request.endDate()
        );
        
        return ApiResponse.success("ok", service.getEventsList(data, request.pageNumber(), request.pageSize()));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<?> getEventDetail(@PathVariable String id) {
        // TODO:取得活動詳細
        return ApiResponse.success("ok");
    }

    @PostMapping("/events/{id}/approve")
    public ApiResponse<?> setEventApprove(@PathVariable String id) {
        // TODO:活動審核通過
        return ApiResponse.success("ok");
    }

    @PostMapping("/events/{id}/request-revision")
    public ApiResponse<?> setEventRevision(@PathVariable String id) {
        // TODO:活動要求補件
        return ApiResponse.success("ok");
    }

    @PostMapping("/events/{id}/map-complete")
    public ApiResponse<?> setEventMapComplete(@PathVariable String id) {
        // TODO:地圖建置完成
        return ApiResponse.success("ok");
    }

    @PostMapping("/events/{id}/unpublish-confirm")
    public ApiResponse<?> setEventUnpublish(@PathVariable String id) {
        // TODO:確認活動下架
        return ApiResponse.success("ok");
    }

    /**
     * 用來獲取管理員後台: 使用者搜尋頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/users/search<br>
     * @param request 搜尋條件與分頁參數，pageNumber從1開始計算
     * @return ApiResponse<T>
     */
    @Schema(description = "獲取管理員後台: 使用者搜尋頁面所需資料")
    @PostMapping("/users/search")
    public ApiResponse<?> getUserList(@RequestBody AdminUserSearchRequest request) {
        AdminUserSearchDto data = new AdminUserSearchDto(
            request.keyWord(),
            request.role(),
            request.status()
        );
        
        return ApiResponse.success("ok", service.getUserList(data, request.pageNumber(), request.pageSize()));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<?> getUserDetail(@PathVariable String id) {
        // TODO:取得使用者詳細
        return ApiResponse.success("ok");
    }

    @PostMapping("/users/{id}/disable")
    public ApiResponse<?> setUserAccountDisable(@PathVariable String id) {
        // TODO:使用者帳號停用
        return ApiResponse.success("ok");
    }

    @PostMapping("/users/{id}/restore")
    public ApiResponse<?> setUserAccountRestore(@PathVariable String id) {
        // TODO:使用者帳號復原
        return ApiResponse.success("ok");
    }

    @PostMapping("/logs/search")
    public ApiResponse<?> getLogList(@RequestBody Map<String, Object> data) {
        // TODO:取得操作紀錄
        return ApiResponse.success("ok");
    }

}
