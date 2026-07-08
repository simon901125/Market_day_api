package com.example.demo.Service;

import java.util.List;

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

public interface AdminServiceInterface {
    /**設定管理員後台: 首頁資料統計部分 */
    AdminDashboardDto setDashboardResponse();
    /**設定管理員後台: 通知中心 */
    Object setNotice(String bookMark, int pageNumber, int pageSize);
    /**設定管理員後台: 活動搜尋 */
    List<AdminEventsItemDto> setEventsList(int pageNumber, int pageSize);
    /**設定管理員後台: 活動搜尋: 搜尋:? */
    List<AdminEventsItemDto> setEventsList(AdminEventSearchDto request, int pageNumber, int pageSize);
    /**設定管理員後台: 活動詳細 */
    AdminEventDetailDto setEventDetail(Long eventId);
    /**設定管理員後台: 使用者搜尋 */
    List<AdminUserItemDto> setUserList(int pageNumber, int pageSize);
    /**設定管理員後台: 使用者搜尋: 搜尋:? */
    List<AdminUserItemDto> setUserList(AdminUserSearchDto request, int pageNumber, int pageSize);
    /**設定管理員後台: 攤主詳細 */
    AdminVenderDetailDto setVenderDetail(Long userId);
    //TODO:感覺還要加 攤主詳細:活動和Logs的setter

    /**設定管理員後台: 主辦方詳細 */
    AdminOrganizerDetailDto setOrganizerDetail(Long userId);
    //TODO:感覺還要加 主辦方詳細:活動和Logs的setter

    /**設定管理員後台: 操作紀錄 */
    AdminLogsDto setLogs(int pageNumber, int pageSize);
    /**設定管理員後台: 操作紀錄 搜尋:? */
    AdminLogsDto setLogs(AdminLogSearchDto request, int pageNumber, int pageSize);
}
