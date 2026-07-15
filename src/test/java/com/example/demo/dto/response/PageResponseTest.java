package com.example.demo.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void normalizesInvalidPaginationAndEmptyInput() {
        PageResponse<String> response = PageResponse.from(null, 0, 0);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isHasPrevious()).isFalse();
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    void slicesItemsAndCalculatesNavigationFlags() {
        PageResponse<Integer> response = PageResponse.from(List.of(1, 2, 3, 4, 5), 2, 2);

        assertThat(response.getItems()).containsExactly(3, 4);
        assertThat(response.getTotalItems()).isEqualTo(5);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isHasPrevious()).isTrue();
        assertThat(response.isHasNext()).isTrue();
    }

    @Test
    void capsPageSizeAndReturnsEmptyOutOfRangePage() {
        PageResponse<Integer> response = PageResponse.from(List.of(1, 2, 3), 3, 100);

        assertThat(response.getPageSize()).isEqualTo(10);
        assertThat(response.getItems()).isEmpty();
        assertThat(response.isHasPrevious()).isTrue();
        assertThat(response.isHasNext()).isFalse();
    }
}
