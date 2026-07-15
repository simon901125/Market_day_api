package com.example.demo.Service;

import org.springframework.lang.NonNull;

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
import com.example.demo.dto.response.admin.StatusLog;

import jakarta.annotation.Nonnull;

public interface AdminServiceInterface {
    /** 設定管理員後台: 首頁資料統計部分 */
    AdminDashboardDto getDashboardResponse();

    /** 設定管理員後台: 通知中心 */
    Object getNotice(String bookMark, int pageNumber, int pageSize);

    /**
     * 設定管理員後台: 活動搜尋
     * 
     * @param request    搜尋條件: {@link AdminEventSearchDto}
     * @param pageNumber 頁碼，base-1: int
     * @param pageSize   每頁筆數: int
     * @return PageResponse<T> {@link PageResponse} 頁碼設定: base-1
     */
    PageResponse<AdminEventListDto> getEventsList(AdminEventSearchDto request, int pageNumber, int pageSize);

    /** 設定管理員後台: 活動詳細 */
    AdminEventDetailDto getEventDetail(@NonNull Long eventId, int pageSize);

    /** 設定管理員後台: 活動詳細:活動狀態變動紀錄 */
    PageResponse<StatusLog> getEventStatusLogs(@NonNull Long eventId, int pageNumber, int pageSize);

    /** 設定管理員後台: 使用者搜尋 */
    PageResponse<AdminUserListDto> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize);

    /** 設定管理員後台: 攤主詳細 */
    AdminVenderDetailDto getVenderDetail(@NonNull Long userId, int pageSize);

    /** 設定管理員後台: 攤主詳細: 活動報名紀錄 */
    PageResponse<AdminVenderRegDto> getVenderRegLogs(@Nonnull Long userId, int pageNumber, int pageSize);

    /** 設定管理員後台: 主辦方詳細 */
    AdminOrgDetailDto getOrganizerDetail(@Nonnull Long userId, int pageSize);

    /** 設定管理員後台: 主辦方詳細 :活動管理紀錄 */
    PageResponse<AdminOrgEventManagementDto> getOrgEventLogs(@Nonnull Long userId, int pageNumber, int pageSize);

    /** 設定管理員後台: 使用者詳細 :使用者登入紀錄 */
    PageResponse<AdminUserLoginDto> getUserLoginLogs(@Nonnull Long userId, int pageNumber, int pageSize);

    /** 設定管理員後台: 操作紀錄搜尋 */
    PageResponse<AdminOperationLogDto> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize);
}
