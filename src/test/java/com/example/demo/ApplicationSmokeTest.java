package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("smoke")
class ApplicationSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void applicationStartsAndServesApiDocumentation() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isNotNull()
                .contains(
                        "\"openapi\"",
                        "\"/api/organizer/newebpay/portal\"",
                        "\"/api/organizer/newebpay/load\"",
                        "\"/api/organizer/newebpay/save\"",
                        "\"/api/organizer/newebpay/verify\"",
                        "\"/api/newebpay/notify\"",
                        "\"/api/newebpay/return\"",
                        "\"/api/newebpay/organizer-verification/return\"");
    }
}
