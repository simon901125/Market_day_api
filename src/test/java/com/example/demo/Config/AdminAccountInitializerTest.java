package com.example.demo.Config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.UserRepository;
import com.example.demo.Service.AuthService;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;
    @Mock
    private ApplicationArguments arguments;

    private AdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminAccountInitializer(userRepository, authService);
    }

    @Test
    void skipsWhenCredentialsAreNotConfigured() {
        configure("", "");

        initializer.run(arguments);

        verify(userRepository, never()).existsByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createsMissingAdminWithHashedPassword() {
        configure("admin@example.test", "secret123");
        when(userRepository.existsByEmail("admin@example.test")).thenReturn(false);
        when(authService.hashPassword("secret123")).thenReturn("hash");

        initializer.run(arguments);

        verify(userRepository).createSystemAdmin("admin@example.test", "hash");
    }

    @Test
    void doesNotReplaceAnExistingNonAdminAccount() {
        configure("admin@example.test", "secret123");
        when(userRepository.existsByEmail("admin@example.test")).thenReturn(true);
        when(userRepository.findProfileByEmail("admin@example.test"))
                .thenReturn(Optional.of(Map.of("role", "VENDOR")));

        initializer.run(arguments);

        verify(userRepository, never()).createSystemAdmin(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(initializer, "adminEmail", email);
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
    }
}
