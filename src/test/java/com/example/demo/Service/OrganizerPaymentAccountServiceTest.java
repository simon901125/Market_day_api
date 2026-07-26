package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.OrganizerPaymentAccountRepository;
import com.example.demo.dto.request.OrganizerPaymentAccountRequest;

@ExtendWith(MockitoExtension.class)
class OrganizerPaymentAccountServiceTest {
    @Mock OrganizerPaymentAccountRepository repository;
    @Mock CredentialEncryptionService encryptionService;
    @Mock JwtService jwtService;
    OrganizerPaymentAccountService service;

    @BeforeEach
    void setUp() {
        service = new OrganizerPaymentAccountService(repository, encryptionService, jwtService);
        when(jwtService.extractTokenFromAuthorizationHeader("Bearer token")).thenReturn("token");
        when(jwtService.isTokenValid("token")).thenReturn(true);
        when(jwtService.getRole("token")).thenReturn("ORGANIZER");
        when(jwtService.getEmail("token")).thenReturn("organizer@test");
        when(repository.findOrganizerByEmail("organizer@test"))
                .thenReturn(Optional.of(Map.of(
                        "userId", 8L,
                        "organizerProfileId", 4L,
                        "role", "ORGANIZER",
                        "userStatus", "ACTIVE")));
    }

    @Test
    void savesEncryptedCredentialsAndNeverReturnsSecrets() {
        when(repository.findByOrganizerProfileId(4L)).thenReturn(Optional.empty());
        when(encryptionService.encrypt("12345678901234567890123456789012")).thenReturn("enc-key");
        when(encryptionService.encrypt("1234567890123456")).thenReturn("enc-iv");
        when(encryptionService.decrypt("enc-key")).thenReturn("12345678901234567890123456789012");
        when(encryptionService.decrypt("enc-iv")).thenReturn("1234567890123456");
        when(repository.upsert(eq(4L), eq("MS123456789"), eq("enc-key"), eq("enc-iv")))
                .thenReturn(12L);
        when(repository.findByOrganizerProfileId(4L))
                .thenReturn(Optional.of(Map.of(
                        "merchantId", "MS123456789",
                        "status", "ACTIVE",
                        "updatedAt", LocalDateTime.of(2026, 7, 26, 10, 30))));

        var response = service.save(
                "Bearer token",
                new OrganizerPaymentAccountRequest(
                        "MS123456789",
                        "12345678901234567890123456789012",
                        "1234567890123456"));

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().merchantId()).isEqualTo("MS123***789");
        assertThat(response.getData().toString()).doesNotContain(
                "12345678901234567890123456789012", "1234567890123456");
        verify(repository).upsert(
                eq(4L), eq("MS123456789"), eq("enc-key"), eq("enc-iv"));
        verify(repository).bindUnboundEvents(8L, 12L);
    }

    @Test
    void rejectsChangingMerchantIdAfterPaymentRecordsExist() {
        when(repository.findByOrganizerProfileId(4L))
                .thenReturn(Optional.of(Map.of(
                        "paymentAccountId", 12L,
                        "merchantId", "MS123456789")));
        when(repository.hasPayments(12L)).thenReturn(true);

        var response = service.save(
                "Bearer token",
                new OrganizerPaymentAccountRequest(
                        "MS987654321",
                        "12345678901234567890123456789012",
                        "1234567890123456"));

        assertThat(response.getStatusCode()).isEqualTo(409);
        assertThat(response.getMessage()).contains("不可更換 MerchantID");
        verify(repository, never()).upsert(
                eq(4L), eq("MS987654321"), eq("enc-key"), eq("enc-iv"));
    }
}
