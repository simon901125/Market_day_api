package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.ImageStorageRepository;
import com.example.demo.Repository.OrganizerRepository;
import com.example.demo.Repository.PaymentRepository;
import com.example.demo.Repository.RequestLogRepository;
import com.example.demo.Repository.StallRepository;

@Tag("integration")
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcRepositorySqlIT extends SqlServerIntegrationTestSupport {
    @Autowired StallRepository stall;
    @Autowired OrganizerRepository organizer;
    @Autowired PaymentRepository payment;
    @Autowired ImageStorageRepository images;
    @Autowired RequestLogRepository requests;

    @Test void stallReadQueriesCompileAgainstCurrentSchema() {
        assertThat(stall.findVendorApplications(-1L, null, null, null)).isEmpty();
        assertThat(stall.findStallId(-1L, "NONE")).isEmpty();
        assertThat(stall.findStallForSelection(-1L, "NONE")).isEmpty();
        assertThat(stall.findApplicationForSelection("NONE")).isEmpty();
        assertThat(stall.findSelectableApplication("NONE")).isEmpty();
        assertThat(stall.findApplicationDatesForSelection(-1L)).isEmpty();
        assertThat(stall.findVendorAccountByEmail("none@example.test")).isEmpty();
        assertThat(stall.findVendorProducts(-1L)).isEmpty();
        assertThat(stall.findActiveCategoriesByIds(List.of(-1L))).isEmpty();
        assertThat(stall.findSelectedStallApplication("NONE", LocalDate.now(), "NONE")).isEmpty();
        assertThat(stall.findSelectedApplicationDates("NONE")).isEmpty();
        assertThat(stall.findVendorStallMapApplication("NONE", LocalDate.now())).isEmpty();
        assertThat(stall.findEventStallsMap(-1L, LocalDate.now())).isEmpty();
        assertThat(stall.findOrganizerStallMapEvent(-1L, -1L)).isEmpty();
        assertThat(stall.findEventForStallStatus(-1L)).isEmpty();
        assertThat(stall.findOrganizerStallMapDetail(-1L, -1L, "NONE", LocalDate.now())).isEmpty();
        assertThat(stall.findEventStallsStatus(-1L, LocalDate.now())).isEmpty();
    }

    @Test void organizerReadQueriesCompileAgainstCurrentSchema() {
        assertThat(organizer.findOrganizerAccountByEmail("none@example.test")).isEmpty();
        assertThat(organizer.findOrganizerAccountingEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerAccountingEventDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findOrganizerAccountingPaymentDetails(-1L)).isEmpty();
        assertThat(organizer.findOrganizerStallEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentEvents(-1L, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerApplications(-1L, null, null, null, null)).isEmpty();
        assertThat(organizer.findOrganizerApplicationDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findVendorApplicationDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findApplicationStatusLogs(-1L)).isEmpty();
        assertThat(organizer.findApplicationEquipmentRentals(-1L)).isEmpty();
        assertThat(organizer.findApplicationDates(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentEventDetail(-1L, -1L)).isEmpty();
        assertThat(organizer.findEventEquipments(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentRentalStats(-1L)).isEmpty();
        assertThat(organizer.findOrganizerEquipmentManagementRows(-1L)).isEmpty();
        assertThat(organizer.findOrganizerPowerManagementRows(-1L)).isEmpty();
        assertThat(organizer.findOrganizerVehicleManagementRows(-1L)).isEmpty();
    }

    @Test void paymentReadQueriesCompileAgainstCurrentSchema() {
        assertThat(payment.findPayableApplication("NONE")).isEmpty();
        assertThat(payment.findPaymentStatusByApplicationNo("NONE")).isEmpty();
        assertThat(payment.findLatestPendingPayment(-1L)).isEmpty();
        assertThat(payment.findPaymentWithApplication("NONE")).isEmpty();
    }

    @Test void imageUpdateQueriesUseAllowedColumnsAndOwnershipChecks() {
        assertThat(images.updateVendorImage("none@example.test", "avatar_image_url", "/images/a.png")).isZero();
        assertThat(images.updateVendorImage("none@example.test", "cover_image_url", "/images/a.png")).isZero();
        assertThat(images.updateProductImage("none@example.test", -1L, "/images/a.png")).isZero();
        assertThat(images.updateEventImage("none@example.test", -1L, "cover_image_url", "/images/a.png")).isZero();
        assertThat(images.updateEventImage("none@example.test", -1L, "map_image_url", "/images/a.png")).isZero();
    }

    @Test void requestLogInsertWorksWithAnonymousRequest() {
        Long id = requests.createRequestLog(null, "POST", "/api/test", 200);
        assertThat(id).isPositive();
    }
}
