package com.example.demo.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void convertsLegacySuccessMapAndSeparatesMessageFromData() {
        ApiResponse<Object> response = ApiResponse.fromLegacy(
                Map.of("message", "Login successful", "token", "jwt"), "Success");

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getMessage()).isEqualTo("登入成功");
        assertThat(response.getData()).isEqualTo(Map.of("token", "jwt"));
    }

    @Test
    void convertsKnownLegacyFailureToClientError() {
        ApiResponse<Object> response = ApiResponse.fromLegacy("User not found", "Success");

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("找不到使用者");
    }

    @Test
    void wrapsOrdinaryPayloadAsSuccess() {
        ApiResponse<Object> response = ApiResponse.fromLegacy(Map.of("id", 1), "Success");

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(Map.of("id", 1));
    }
}
