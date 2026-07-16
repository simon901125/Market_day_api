package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService();
        ReflectionTestUtils.setField(
                service,
                "jwtSecret",
                "integration-test-secret-key-that-is-long-enough-for-hs256-signing");
        ReflectionTestUtils.setField(service, "expirationMs", 3_600_000L);
    }

    @Test
    void generatesAndReadsValidToken() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30).withNano(0);
        String token = service.generateToken("vendor@example.test", "VENDOR", expiresAt);

        assertThat(service.isTokenValid(token)).isTrue();
        assertThat(service.getEmail(token)).isEqualTo("vendor@example.test");
        assertThat(service.getRole(token)).isEqualTo("VENDOR");
        assertThat(service.getExpiration(token)).isEqualTo(expiresAt);
    }

    @Test
    void revokedOrMalformedTokenIsInvalid() {
        String token = service.generateToken(
                "vendor@example.test",
                "VENDOR",
                LocalDateTime.now().plusMinutes(30));
        service.revokeToken(token);

        assertThat(service.isTokenValid(token)).isFalse();
        assertThat(service.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void calculatesConfiguredExpirationAndExtractsBearerToken() {
        LocalDateTime before = LocalDateTime.now().plusHours(1);
        LocalDateTime calculated = service.calculateExpiration();

        assertThat(Duration.between(before, calculated).abs()).isLessThan(Duration.ofSeconds(2));
        assertThat(service.extractTokenFromAuthorizationHeader("Bearer abc")).isEqualTo("abc");
        assertThat(service.extractTokenFromAuthorizationHeader("Basic abc")).isNull();
        assertThat(service.extractTokenFromAuthorizationHeader(null)).isNull();
    }
}
