package com.example.demo.Filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.demo.Service.RequestLogService;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private RequestLogService requestLogService;

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter(requestLogService);
    }

    @Test
    void logsMutationUsingApiResponseStatusAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/resource");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> {
            wrappedRequest.setAttribute(RequestLogService.RESPONSE_STATUS_CODE_ATTRIBUTE, 400);
        };

        filter.doFilter(request, response, chain);

        verify(requestLogService).recordRequest(any(), org.mockito.ArgumentMatchers.eq(400));
    }

    @Test
    void skipsGetAndNonApiRequests() throws Exception {
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/resource"), new MockHttpServletResponse(), firstChain);
        assertThat(firstChain.getRequest()).isNotNull();

        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/health"), new MockHttpServletResponse(), secondChain);

        verify(requestLogService, never()).recordRequest(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
