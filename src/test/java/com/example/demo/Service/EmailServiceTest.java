package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
    @Mock JavaMailSender sender;
    EmailService service;

    @BeforeEach void setUp() {
        service = new EmailService();
        ReflectionTestUtils.setField(service, "mailSender", sender);
        ReflectionTestUtils.setField(service, "fromEmail", "system@example.test");
    }

    @Test void verificationMessageContainsRecipientSenderAndCode() {
        service.sendVerificationCode("vendor@example.test", "123456");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("system@example.test");
        assertThat(captor.getValue().getTo()).containsExactly("vendor@example.test");
        assertThat(captor.getValue().getText()).contains("123456");
    }

    @Test void passwordResetOmitsBlankSenderAndContainsCode() {
        ReflectionTestUtils.setField(service, "fromEmail", " ");
        service.sendPasswordResetCode("vendor@example.test", "654321");
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isNull();
        assertThat(captor.getValue().getText()).contains("654321");
    }
}
