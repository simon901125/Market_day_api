package com.example.demo.Controller;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.OrganizerService;
import com.example.demo.Service.OrganizerNotificationService;
import com.example.demo.Service.OrganizerService.ReportExport;
import com.example.demo.Service.StallService;
import com.example.demo.dto.request.OrganizerApplicationReviewRequest;
import com.example.demo.dto.request.OrganizerProfileSaveRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.OrganizerAccountResponse;
import com.example.demo.dto.response.OrganizerAccountingSearchResponse;
import com.example.demo.dto.response.OrganizerApplicationDetailResponse;
import com.example.demo.dto.response.OrganizerApplicationSearchResponse;
import com.example.demo.dto.response.OrganizerDashboardInitResponse;
import com.example.demo.dto.response.OrganizerNotificationSearchResponse;
import com.example.demo.dto.response.OrganizerEquipmentSearchResponse;
import com.example.demo.dto.response.OrganizerEventSearchResponse;
import com.example.demo.dto.response.OrganizerEventDetailResponse;
import com.example.demo.dto.response.OrganizerStallEventSearchResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "主辦方 API", description = "主辦方帳務、報名、設備、攤位與個人資料 API")
public class OrganizerController {

    @Autowired
    private OrganizerService organizerService;

    @Autowired
    private OrganizerNotificationService organizerNotificationService;

    @Autowired
    private StallService stallService;

    @Operation(summary = "初始化主辦方後台", description = "登入後判斷目前主辦方是否需要填寫基本資料")
    @GetMapping("/api/organizer/dashboard/init")
    public ApiResponse<OrganizerDashboardInitResponse> initOrganizerDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return organizerService.initOrganizerDashboard(authorizationHeader);
    }

    @Operation(summary = "查詢主辦方活動", description = "回傳主辦方待辦統計與活動列表，支援搜尋、狀態、日期、排序及分頁")
    @GetMapping("/api/organizer/events/search")
    public ApiResponse<OrganizerEventSearchResponse> searchOrganizerEvents(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "sort", required = false, defaultValue = "DEFAULT") String sort,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "6") Integer pageSize) {
        return organizerService.searchOrganizerEvents(
                authorizationHeader, keyword, status, startDate, endDate, sort, page, pageSize);
    }

    @Operation(summary = "取得主辦方活動詳情", description = "取得目前登入主辦方所屬活動的完整查看及編輯資料")
    @GetMapping("/api/organizer/events/{eventId}")
    public ApiResponse<OrganizerEventDetailResponse> getOrganizerEventDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId) {
        return organizerService.getOrganizerEventDetail(authorizationHeader, eventId);
    }

    @Operation(summary = "取得主辦方通知中心", description = "查詢目前登入主辦方通知；支援全部、未讀、報名相關、付款相關、活動異動及系統公告分類，未讀通知優先。")
    @GetMapping("/api/organizer/notices")
    public ApiResponse<OrganizerNotificationSearchResponse> getOrganizerNotifications(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "filter", defaultValue = "全部") String filter,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return organizerNotificationService.getNotifications(authorizationHeader, filter, page, pageSize);
    }

    @Operation(summary = "查詢主辦方帳務活動列表", description = "依活動名稱、狀態、活動日期與分頁查詢主辦方帳務活動。")
    @GetMapping("/api/organizer/accounts/search")
    public ApiResponse<OrganizerAccountingSearchResponse> searchOrganizerAccounts(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
            @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        return organizerService.searchOrganizerAccounts(
                authorizationHeader,
                eventTitle,
                status,
                eventStartAt,
                eventEndAt,
                page,
                pageSize);
    }

    @Operation(summary = "查詢主辦方活動帳務詳情", description = "依活動 ID 查詢帳務摘要、統計與付款明細。")
    @GetMapping("/api/organizer/accounts/{eventId}")
    public ApiResponse<MapBackedResponse> getOrganizerAccountDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "paymentPage", required = false, defaultValue = "1") Integer paymentPage,
            @RequestParam(value = "paymentPageSize", required = false, defaultValue = "10") Integer paymentPageSize) {
        return organizerService.getOrganizerAccountDetail(
                authorizationHeader,
                eventId,
                status,
                paymentPage,
                paymentPageSize);
    }

    @Operation(summary = "匯出主辦方活動帳務報表", description = "依活動 ID 匯出帳務 Excel 報表。")
    @GetMapping("/api/organizer/accounts/{eventId}/export")
    public ResponseEntity<byte[]> exportOrganizerAccountReport(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "status", required = false) String status) {
        return reportResponse(organizerService.exportOrganizerAccountReport(authorizationHeader, eventId, status));
    }

    @Operation(summary = "載入主辦方資料", description = "取得目前登入主辦方的基本資料與服務時間。")
    @GetMapping("/api/organizer/profile/load")
    public ApiResponse<OrganizerAccountResponse> loadOrganizerProfile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return organizerService.loadOrganizerProfile(authorizationHeader);
    }

    @Operation(summary = "儲存主辦方資料", description = "更新目前登入主辦方的基本資料與服務時間。")
    @PostMapping("/api/organizer/profile/save")
    public ApiResponse<OrganizerAccountResponse> saveOrganizerProfile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) OrganizerProfileSaveRequest body) {
        return organizerService.saveOrganizerProfile(authorizationHeader, body);
    }

    @Operation(summary = "查詢主辦方報名列表", description = "依活動、狀態、品牌與報名日期查詢報名列表。")
    @GetMapping("/api/organizer/applications/search")
    public ApiResponse<OrganizerApplicationSearchResponse> searchOrganizerApplications(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "brandName", required = false) String brandName,
            @RequestParam(value = "registration_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registrationStartAt,
            @RequestParam(value = "registration_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registrationEndAt,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        return organizerService.searchOrganizerApplications(
                authorizationHeader,
                eventTitle,
                status,
                brandName,
                registrationStartAt,
                registrationEndAt,
                page,
                pageSize);
    }

    @Operation(summary = "查詢主辦方攤位活動列表", description = "查詢主辦方攤位管理活動列表。")
    @GetMapping("/api/organizer/stalls/search")
    public ApiResponse<OrganizerStallEventSearchResponse> searchOrganizerStallEvents(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
            @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        return organizerService.searchOrganizerStallEvents(
                authorizationHeader,
                eventTitle,
                status,
                eventStartAt,
                eventEndAt,
                page,
                pageSize);
    }

    @Operation(summary = "查詢主辦方設備活動列表", description = "查詢主辦方設備租借活動列表。")
    @GetMapping("/api/organizer/equipment/search")
    public ApiResponse<OrganizerEquipmentSearchResponse> searchOrganizerEquipmentEvents(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
            @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        return organizerService.searchOrganizerEquipmentEvents(
                authorizationHeader,
                eventTitle,
                status,
                eventStartAt,
                eventEndAt,
                page,
                pageSize);
    }

    @Operation(summary = "查詢主辦方活動設備詳情", description = "查詢活動設備、用電、租借統計與管理列表。")
    @GetMapping("/api/organizer/equipment/{eventId}")
    public ApiResponse<MapBackedResponse> getOrganizerEquipmentDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "equipmentRentalPage", required = false, defaultValue = "1") Integer equipmentRentalPage,
            @RequestParam(value = "equipmentRentalPageSize", required = false, defaultValue = "10") Integer equipmentRentalPageSize,
            @RequestParam(value = "extraPowerPage", required = false, defaultValue = "1") Integer extraPowerPage,
            @RequestParam(value = "extraPowerPageSize", required = false, defaultValue = "10") Integer extraPowerPageSize,
            @RequestParam(value = "vehiclePage", required = false, defaultValue = "1") Integer vehiclePage,
            @RequestParam(value = "vehiclePageSize", required = false, defaultValue = "10") Integer vehiclePageSize) {
        return organizerService.getOrganizerEquipmentDetail(
                authorizationHeader,
                eventId,
                equipmentRentalPage,
                equipmentRentalPageSize,
                extraPowerPage,
                extraPowerPageSize,
                vehiclePage,
                vehiclePageSize);
    }

    @Operation(summary = "匯出主辦方活動設備報表", description = "依活動 ID 匯出設備 Excel 報表。")
    @GetMapping("/api/organizer/equipment/{eventId}/export")
    public ResponseEntity<byte[]> exportOrganizerEquipmentReport(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId) {
        return reportResponse(organizerService.exportOrganizerEquipmentReport(authorizationHeader, eventId));
    }

    @Operation(summary = "查詢主辦方報名詳情", description = "依報名 ID 查詢報名詳細資料。")
    @GetMapping("/api/organizer/applications/{id}")
    public ApiResponse<OrganizerApplicationDetailResponse> getOrganizerApplicationDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        return organizerService.getOrganizerApplicationDetail(authorizationHeader, id);
    }

    @Operation(summary = "審核通過主辦方報名", description = "主辦方審核通過指定報名。")
    @PostMapping("/api/organizer/applications/{id}/approve")
    public ApiResponse<MapBackedResponse> approveOrganizerApplication(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        return organizerService.approveOrganizerApplication(authorizationHeader, id);
    }

    @Operation(summary = "退回主辦方報名", description = "主辦方退回指定報名並可填寫審核原因。")
    @PostMapping("/api/organizer/applications/{id}/reject")
    public ApiResponse<MapBackedResponse> rejectOrganizerApplication(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestBody(required = false) OrganizerApplicationReviewRequest body) {
        return organizerService.rejectOrganizerApplication(authorizationHeader, id, body);
    }

    @Operation(summary = "查詢主辦方攤位地圖", description = "依活動 ID 與日期查詢活動資訊與攤位地圖。")
    @GetMapping("/api/organizer/stall/{eventId}")
    public ApiResponse<MapBackedResponse> getOrganizerStallMap(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "applyDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applyDate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        return stallService.getOrganizerStallMap(authorizationHeader, eventId, applyDate, keyword, status);
    }

    @Operation(summary = "查詢主辦方單一攤位詳情", description = "依活動 ID、攤位編號與日期查詢攤位與已選攤主資料。")
    @GetMapping("/api/organizer/stall/{eventId}/{stallNo}")
    public ApiResponse<MapBackedResponse> getOrganizerStallMapDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @PathVariable String stallNo,
            @RequestParam(value = "applyDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applyDate) {
        return stallService.getOrganizerStallMapDetail(authorizationHeader, eventId, stallNo, applyDate);
    }

    private ResponseEntity<byte[]> reportResponse(ReportExport export) {
        if (!export.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(export.errorMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .contentLength(export.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.filename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(export.content());
    }
}
