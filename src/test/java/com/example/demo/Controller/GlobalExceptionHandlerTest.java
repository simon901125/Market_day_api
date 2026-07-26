package com.example.demo.Controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.demo.exception.ConflictException;

class GlobalExceptionHandlerTest {
    @Test void oversizedUploadReturnsBadRequestApiResponse() {
        var response = new GlobalExceptionHandler()
                .handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(5));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccessStatus()).isFalse();
    }

    @Test void conflictReturnsHttp409WithOriginalMessage() {
        var response = new GlobalExceptionHandler()
                .handleConflictException(new ConflictException("正在報名無法下架活動"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatusCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("正在報名無法下架活動");
    }
}
