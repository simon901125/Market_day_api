package com.example.demo.Controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {
    @Test void oversizedUploadReturnsBadRequestApiResponse() {
        var response = new GlobalExceptionHandler()
                .handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(5));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccessStatus()).isFalse();
    }
}
