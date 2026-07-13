package com.example.demo.Service;

import org.springframework.lang.NonNull;

import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.admin.AdminOrganizerDetailDto;
import com.example.demo.dto.response.admin.AdminVenderDetailDto;

import jakarta.annotation.Nonnull;

public interface AdminServiceInterface {
    /**設定管理員後台: 首頁資料統計部分 */
    AdminDashboardDto getDashboardResponse();
    /**設定管理員後台: 通知中心 */
    Object getNotice(String bookMark, int pageNumber, int pageSize);
    /**
     * 設定管理員後台: 活動搜尋
     * @param request 搜尋條件: {@link AdminEventSearchDto}
     * @param pageNumber 頁碼，base-1: int 
     * @param pageSize 每頁筆數: int
     * @return PageResponse<T> {@link PageResponse} 頁碼設定: base-1
     */
    PageResponse<?> getEventsList(AdminEventSearchDto request, int pageNumber, int pageSize);
    /**設定管理員後台: 活動詳細 */
    AdminEventDetailDto getEventDetail(@NonNull Long eventId);
    /**設定管理員後台: 使用者搜尋 */
    PageResponse<?> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize);
    /**設定管理員後台: 攤主詳細 */
    AdminVenderDetailDto getVenderDetail(@NonNull Long userId);
    //TODO:感覺還要加 攤主詳細:活動和Logs的getter

    /**設定管理員後台: 主辦方詳細 */
    AdminOrganizerDetailDto getOrganizerDetail(@Nonnull Long userId);
    //TODO:感覺還要加 主辦方詳細:活動和Logs的getter

    /**設定管理員後台: 操作紀錄 */
    PageResponse<?> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize);
}
