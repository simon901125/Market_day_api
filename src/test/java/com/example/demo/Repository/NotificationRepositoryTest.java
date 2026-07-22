package com.example.demo.Repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationRepositoryTest {

    @Test
    void removesEventIdLabelFromVendorAndOrganizerDisplayContent() {
        assertThat(NotificationRepository.displayContent(
                "活動「夏日市集」（活動 ID：123）已通過審核"))
                .isEqualTo("活動「夏日市集」已通過審核");
        assertThat(NotificationRepository.displayContent(
                "活動「夏日市集」（活動 ID: ）已取消"))
                .isEqualTo("活動「夏日市集」已取消");
    }

    @Test
    void preservesReasonWhenRemovingEventIdLabel() {
        assertThat(NotificationRepository.displayContent(
                "活動下架申請（活動 ID：123，原因：天候不佳）"))
                .isEqualTo("活動下架申請（原因：天候不佳）");
    }
}
