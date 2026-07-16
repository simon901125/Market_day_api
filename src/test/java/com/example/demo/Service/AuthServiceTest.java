package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final AuthService service = new AuthService();

    @Test
    void hashesAndMatchesPassword() {
        String hash = service.hashPassword("a12345678");

        assertThat(hash).isNotEqualTo("a12345678");
        assertThat(service.matchesPassword("a12345678", hash)).isTrue();
        assertThat(service.matchesPassword("wrongPassword1", hash)).isFalse();
    }

    @Test
    void rejectsMissingOrMalformedHash() {
        assertThat(service.matchesPassword(null, "hash")).isFalse();
        assertThat(service.matchesPassword("password", null)).isFalse();
        assertThat(service.matchesPassword("password", "")).isFalse();
        assertThat(service.matchesPassword("password", "not-bcrypt")).isFalse();
    }
}
