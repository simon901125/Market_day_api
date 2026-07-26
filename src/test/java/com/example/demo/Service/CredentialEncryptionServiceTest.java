package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CredentialEncryptionServiceTest {

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutExposingPlaintext() {
        CredentialEncryptionService service =
                new CredentialEncryptionService("0123456789abcdef0123456789abcdef");

        String first = service.encrypt("12345678901234567890123456789012");
        String second = service.encrypt("12345678901234567890123456789012");

        assertThat(first).startsWith("v1:").doesNotContain("12345678901234567890123456789012");
        assertThat(second).isNotEqualTo(first);
        assertThat(service.decrypt(first)).isEqualTo("12345678901234567890123456789012");
    }

    @Test
    void refusesToOperateWithoutStrongMasterKey() {
        CredentialEncryptionService service = new CredentialEncryptionService("too-short");
        assertThatThrownBy(() -> service.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_CREDENTIAL_ENCRYPTION_KEY");
    }
}
