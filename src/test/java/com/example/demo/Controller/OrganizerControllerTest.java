package com.example.demo.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Service.OrganizerService;
import com.example.demo.Service.OrganizerNotificationService;
import com.example.demo.Service.OrganizerService.ReportExport;
import com.example.demo.Service.StallService;
import com.example.demo.dto.request.OrganizerApplicationReviewRequest;
import com.example.demo.dto.request.OrganizerProfileSaveRequest;
import com.example.demo.dto.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class OrganizerControllerTest {
    @Mock OrganizerService organizerService;
    @Mock OrganizerNotificationService organizerNotificationService;
    @Mock StallService stallService;
    OrganizerController controller;
    static final String AUTH = "Bearer token";

    @BeforeEach void setUp() {
        controller = new OrganizerController();
        ReflectionTestUtils.setField(controller, "organizerService", organizerService);
        ReflectionTestUtils.setField(controller, "organizerNotificationService", organizerNotificationService);
        ReflectionTestUtils.setField(controller, "stallService", stallService);
    }

    @Test void notificationEndpointDelegatesAllFiltersAndPages() {
        controller.getOrganizerNotifications(AUTH, "報名相關", 2, 10);
        verify(organizerNotificationService).getNotifications(AUTH, "報名相關", 2, 10);
    }

    @Test void accountSearchDetailAndProfileEndpointsDelegate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        controller.searchOrganizerAccounts(AUTH, "market", "PAID", start, end, 2, 25);
        controller.getOrganizerAccountDetail(AUTH, 4L, "PAID", 3, 15);
        controller.loadOrganizerProfile(AUTH);
        OrganizerProfileSaveRequest request = new OrganizerProfileSaveRequest();
        controller.saveOrganizerProfile(AUTH, request);
        verify(organizerService).searchOrganizerAccounts(AUTH, "market", "PAID", start, end, 2, 25);
        verify(organizerService).getOrganizerAccountDetail(AUTH, 4L, "PAID", 3, 15);
        verify(organizerService).loadOrganizerProfile(AUTH);
        verify(organizerService).saveOrganizerProfile(AUTH, request);
    }

    @Test void applicationSearchDetailApproveAndRejectDelegate() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);
        controller.searchOrganizerApplications(AUTH, "event", "PENDING", "brand", start, end, 1, 20);
        controller.getOrganizerApplicationDetail(AUTH, 8L);
        controller.approveOrganizerApplication(AUTH, 8L);
        OrganizerApplicationReviewRequest request = new OrganizerApplicationReviewRequest();
        controller.rejectOrganizerApplication(AUTH, 8L, request);
        verify(organizerService).searchOrganizerApplications(AUTH, "event", "PENDING", "brand", start, end, 1, 20);
        verify(organizerService).getOrganizerApplicationDetail(AUTH, 8L);
        verify(organizerService).approveOrganizerApplication(AUTH, 8L);
        verify(organizerService).rejectOrganizerApplication(AUTH, 8L, request);
    }

    @Test void depositRefundDelegatesIdsAndUsesApiHttpStatus() {
        when(organizerService.refundOrganizerDeposit(AUTH, 8L))
                .thenReturn(ApiResponse.fail(409, "目前不符合保證金退還條件"));

        var response = controller.refundOrganizerDeposit(AUTH, 8L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(organizerService).refundOrganizerDeposit(AUTH, 8L);
    }

    @Test void stallAndEquipmentEndpointsDelegateAllFiltersAndPages() {
        LocalDate date = LocalDate.of(2026, 8, 2);
        controller.searchOrganizerStallEvents(AUTH, "event", "OPEN", date, date, 2, 5);
        controller.searchOrganizerEquipmentEvents(AUTH, "event", "OPEN", date, date, 3, 6);
        controller.getOrganizerEquipmentDetail(AUTH, 9L, 1, 10, 2, 20, 3, 30);
        controller.getOrganizerStallMap(AUTH, 9L, date, "A", "ASSIGNED");
        controller.getOrganizerStallMapDetail(AUTH, 9L, "A01", date);
        verify(organizerService).searchOrganizerStallEvents(AUTH, "event", "OPEN", date, date, 2, 5);
        verify(organizerService).searchOrganizerEquipmentEvents(AUTH, "event", "OPEN", date, date, 3, 6);
        verify(organizerService).getOrganizerEquipmentDetail(AUTH, 9L, 1, 10, 2, 20, 3, 30);
        verify(stallService).getOrganizerStallMap(AUTH, 9L, date, "A", "ASSIGNED");
        verify(stallService).getOrganizerStallMapDetail(AUTH, 9L, "A01", date);
    }

    @Test void reportExportsReturnAttachmentOrBadRequest() {
        when(organizerService.exportOrganizerAccountReport(AUTH, 3L, "PAID"))
                .thenReturn(new ReportExport(true, new byte[] {1, 2}, "report.xlsx", "application/test", null));
        when(organizerService.exportOrganizerEquipmentReport(AUTH, 3L))
                .thenReturn(new ReportExport(false, null, null, null, "cannot export"));
        var success = controller.exportOrganizerAccountReport(AUTH, 3L, "PAID");
        var failure = controller.exportOrganizerEquipmentReport(AUTH, 3L);
        assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(success.getHeaders().getContentDisposition().getFilename()).isEqualTo("report.xlsx");
        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new String(failure.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("cannot export");
    }
}
