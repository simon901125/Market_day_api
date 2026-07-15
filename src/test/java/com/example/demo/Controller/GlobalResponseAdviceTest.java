package com.example.demo.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.demo.Service.RequestLogService;
import com.example.demo.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

class GlobalResponseAdviceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GlobalResponseAdvice advice = new GlobalResponseAdvice(objectMapper);
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private MockHttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(URI.create("http://localhost/api/test"));
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        servletRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void wrapsLegacyBodyAndStoresRequestLogMetadata() {
        Object result = advice.beforeBodyWrite(Map.of("id", 9), null, MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class, request, response);

        assertThat(result).isInstanceOf(ApiResponse.class);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertThat(apiResponse.getMessageDetails()).isEqualTo("Executed API: POST /api/test");
        assertThat(servletRequest.getAttribute(RequestLogService.RESPONSE_STATUS_CODE_ATTRIBUTE)).isEqualTo(200);
        assertThat(servletRequest.getAttribute(RequestLogService.API_RESPONSE_ATTRIBUTE)).isSameAs(apiResponse);
    }

    @Test
    void serializesWrappedStringWhenStringConverterWasSelected() throws Exception {
        Object result = advice.beforeBodyWrite("Login successful", null, MediaType.TEXT_PLAIN,
                StringHttpMessageConverter.class, request, response);

        assertThat(result).isInstanceOf(String.class);
        assertThat(objectMapper.readTree((String) result).get("statusCode").asInt()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void leavesBinaryPayloadUntouched() {
        byte[] body = "zip".getBytes(StandardCharsets.UTF_8);

        Object result = advice.beforeBodyWrite(body, null, MediaType.parseMediaType("application/zip"),
                MappingJackson2HttpMessageConverter.class, request, response);

        assertThat(result).isSameAs(body);
    }
}
