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
import com.example.demo.Service.OrganizerService.ReportExport;
import com.example.demo.Service.StallService;
import com.example.demo.dto.request.OrganizerApplicationReviewRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.MapBackedResponse;
import com.example.demo.dto.response.OrganizerAccountResponse;
import com.example.demo.dto.response.OrganizerAccountingSearchResponse;
import com.example.demo.dto.response.OrganizerApplicationDetailResponse;
import com.example.demo.dto.response.OrganizerApplicationSearchResponse;
import com.example.demo.dto.response.OrganizerEquipmentSearchResponse;
import com.example.demo.dto.response.OrganizerStallEventSearchResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "主辦方 API", description = "提供主辦方帳號與主辦資料相關功能")
public class OrganizerController {

    @Autowired
    private OrganizerService organizerService;

    @Autowired
    private StallService stallService;

    @Operation(summary = "查詢主辦方帳務列表", description = "未輸入條件時回傳目前主辦方發起的所有活動帳務，有條件時依活動名稱、發布狀態與活動日期篩選。")
    @GetMapping("/api/organizer/accounts/search")
    public ApiResponse<OrganizerAccountingSearchResponse> searchOrganizerAccounts(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
            @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt) {
        return organizerService.searchOrganizerAccounts(
                authorizationHeader,
                eventTitle,
                status,
                eventStartAt,
                eventEndAt);
    }

    @Operation(summary = "取得主辦方活動帳務詳情", description = "依活動 ID 取得活動帳務摘要、統計與付款明細。")
    @GetMapping("/api/organizer/accounts/{eventId}")
    public ApiResponse<MapBackedResponse> getOrganizerAccountDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "status", required = false) String status) {
        return organizerService.getOrganizerAccountDetail(authorizationHeader, eventId, status);
    }

    @Operation(summary = "匯出主辦方活動帳務報表", description = "依活動 ID 產出帳務 Excel 報表，內含活動資訊、帳務摘要與付款明細工作表。")
    @GetMapping("/api/organizer/accounts/{eventId}/export")
    public ResponseEntity<byte[]> exportOrganizerAccountReport(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "status", required = false) String status) {
        return reportResponse(organizerService.exportOrganizerAccountReport(authorizationHeader, eventId, status));
    }

    @Operation(summary = "取得主辦方帳號資訊", description = "回傳目前登入主辦方的主辦方名稱、聯絡資訊、公司資訊、地址與服務時間。")
    @GetMapping("/api/organizer/account")
    public ApiResponse<OrganizerAccountResponse> getOrganizerAccount(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return organizerService.getOrganizerAccount(authorizationHeader);
    }

    @Operation(summary = "查詢主辦方報名列表", description = "依 Authorization 取得目前登入主辦方，並依活動名稱、狀態、品牌名稱、報名時間區間篩選報名資料。")
    @GetMapping("/api/organizer/applications/search")
    public ApiResponse<OrganizerApplicationSearchResponse> searchOrganizerApplications(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "brandName", required = false) String brandName,
            @RequestParam(value = "registration_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registrationStartAt,
            @RequestParam(value = "registration_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate registrationEndAt) {
        return organizerService.searchOrganizerApplications(
                authorizationHeader,
                eventTitle,
                status,
                brandName,
                registrationStartAt,
                registrationEndAt);
    }

    @Operation(summary = "搜尋主辦方攤位管理活動", description = "依活動名稱、狀態與活動日期區間查詢可進入攤位管理的活動列表。")
    @GetMapping("/api/organizer/stalls/search")
    public ApiResponse<OrganizerStallEventSearchResponse> searchOrganizerStallEvents(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
            @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt) {
        return organizerService.searchOrganizerStallEvents(
                authorizationHeader,
                eventTitle,
                status,
                eventStartAt,
                eventEndAt);
    }

    @Operation(summary = "查詢主辦方設備租借活動", description = "依活動名稱、狀態與活動日期區間查詢目前主辦方活動的設備租借摘要。")
    @GetMapping("/api/organizer/equipment/search")
    public ApiResponse<OrganizerEquipmentSearchResponse> searchOrganizerEquipmentEvents(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(value = "eventTitle", required = false) String eventTitle,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "event_start_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStartAt,
            @RequestParam(value = "event_end_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEndAt) {
        return organizerService.searchOrganizerEquipmentEvents(
                authorizationHeader,
                eventTitle,
                status,
                eventStartAt,
                eventEndAt);
    }

    @Operation(summary = "取得主辦方活動設備詳情", description = "依活動 ID 取得活動資訊、設備設定、設備租借統計、用電統計與攤商設備管理資料。")
    @GetMapping("/api/organizer/equipment/{eventId}")
    public ApiResponse<MapBackedResponse> getOrganizerEquipmentDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId) {
        return organizerService.getOrganizerEquipmentDetail(authorizationHeader, eventId);
    }

    @Operation(summary = "匯出主辦方活動設備報表", description = "依活動 ID 產出設備 Excel 報表，內含設備、用電、統計與管理列表工作表。")
    @GetMapping("/api/organizer/equipment/{eventId}/export")
    public ResponseEntity<byte[]> exportOrganizerEquipmentReport(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId) {
        return reportResponse(organizerService.exportOrganizerEquipmentReport(authorizationHeader, eventId));
    }

    @Operation(summary = "取得主辦方報名詳情", description = "依報名 ID 取得目前主辦方活動底下的攤商報名資料、攤位日期、設備租借與審核狀態流程。")
    @GetMapping("/api/organizer/applications/{id}")
    public ApiResponse<OrganizerApplicationDetailResponse> getOrganizerApplicationDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        return organizerService.getOrganizerApplicationDetail(authorizationHeader, id);
    }

    @Operation(summary = "通過主辦活動報名", description = "主辦方針對指定報名申請審核通過。")
    @PostMapping("/api/organizer/applications/{id}/approve")
    public ApiResponse<MapBackedResponse> approveOrganizerApplication(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        return organizerService.approveOrganizerApplication(authorizationHeader, id);
    }

    @Operation(summary = "退回主辦活動報名", description = "主辦方針對指定報名申請審核不通過，可附上不通過原因。")
    @PostMapping("/api/organizer/applications/{id}/reject")
    public ApiResponse<MapBackedResponse> rejectOrganizerApplication(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestBody(required = false) OrganizerApplicationReviewRequest body) {
        return organizerService.rejectOrganizerApplication(authorizationHeader, id, body);
    }

    @Operation(summary = "取得主辦方活動攤位詳情", description = "依 eventId 取得主辦方活動資訊與全部攤位狀態。")
    @GetMapping("/api/organizer/stall/{eventId}")
    public ApiResponse<MapBackedResponse> getOrganizerStallMap(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long eventId,
            @RequestParam(value = "applyDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applyDate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        return stallService.getOrganizerStallMap(authorizationHeader, eventId, applyDate, keyword, status);
    }

    @Operation(summary = "取得活動各攤位登記資料", description = "依 eventId 與 stallNo 取得攤位資訊；若已被登記，會回傳攤商與申請資料。")
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
