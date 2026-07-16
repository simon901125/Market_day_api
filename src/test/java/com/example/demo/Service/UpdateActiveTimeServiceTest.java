package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UpdateActiveTimeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private UpdateActiveTimeService service;

    @Test
    void delegatesSessionIdentityAndExpirationToRepository() {
        LocalDateTime expiration = LocalDateTime.now().plusMinutes(30);
        when(jwtService.getEmail("token")).thenReturn("vendor@example.test");
        when(jwtService.getRole("token")).thenReturn("VENDOR");
        when(jwtService.getExpiration("token")).thenReturn(expiration);
        when(userRepository.isCurrentLoginSession("vendor@example.test", "VENDOR", expiration)).thenReturn(true);

        assertThat(service.isCurrentLoginSession("token")).isTrue();
        verify(userRepository).isCurrentLoginSession("vendor@example.test", "VENDOR", expiration);
    }
}
