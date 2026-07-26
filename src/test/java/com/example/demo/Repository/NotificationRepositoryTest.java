package com.example.demo.Repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.demo.dto.notification.NotificationContentSanitizer;

class NotificationRepositoryTest {

    @Test
    void removesApplicationIdFieldWithOrWithoutAValue() {
        assertThat(NotificationRepository.displayContent(
                "活動「夏日市集」已收到您的報名申請（報名 ID：123），目前等待主辦方審核"))
                .isEqualTo("活動「夏日市集」已收到您的報名申請，目前等待主辦方審核");
        assertThat(NotificationRepository.displayContent(
                "活動「夏日市集」的報名（報名 ID：）已取消"))
                .isEqualTo("活動「夏日市集」的報名已取消");
    }

    @Test
    void removesEveryRoutingIdLabelFromDisplayContent() {
        assertThat(NotificationContentSanitizer.sanitize(
                "活動「夏日市集」（活動 ID：88）已送出審核"))
                .isEqualTo("活動「夏日市集」已送出審核");
        assertThat(NotificationContentSanitizer.sanitize(
                "退款申請（退款 ID：9）已送出"))
                .isEqualTo("退款申請已送出");
        assertThat(NotificationContentSanitizer.sanitize(
                "攤位選擇（報名 ID：20）已完成"))
                .isEqualTo("攤位選擇已完成");
    }

    @Test
    void preservesNonIdMetadataInsideTheSameParentheses() {
        assertThat(NotificationContentSanitizer.sanitize(
                "主辦方「測試」（帳號：owner@example.com，使用者 ID：）已送出註冊審核"))
                .isEqualTo("主辦方「測試」（帳號：owner@example.com）已送出註冊審核");
        assertThat(NotificationContentSanitizer.sanitize(
                "活動下架申請（活動 ID：123，原因：天候不佳）"))
                .isEqualTo("活動下架申請（原因：天候不佳）");
    }
}
