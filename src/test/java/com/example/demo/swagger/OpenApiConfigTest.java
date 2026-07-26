package com.example.demo.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void usesConfiguredHttpsPublicBaseUrlWithoutTrailingSlash() {
        var openApi = new OpenApiConfig(" https://api.marketday.dev/ ").marketDayOpenAPI();

        assertThat(openApi.getServers())
                .singleElement()
                .extracting(server -> server.getUrl())
                .isEqualTo("https://api.marketday.dev");
    }

    @Test
    void leavesServersUnsetForLocalDynamicRequestResolution() {
        var openApi = new OpenApiConfig(" ").marketDayOpenAPI();

        assertThat(openApi.getServers()).isNullOrEmpty();
    }
}
