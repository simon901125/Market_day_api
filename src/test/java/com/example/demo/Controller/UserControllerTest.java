package com.example.demo.Controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.demo.Service.UserService;
import com.example.demo.dto.request.EmailVerificationRequest;
import com.example.demo.dto.request.GoogleCredentialRequest;
import com.example.demo.dto.request.LocalLoginRequest;
import com.example.demo.dto.request.LocalRegisterRequest;
import com.example.demo.dto.request.RequestPasswordResetRequest;
import com.example.demo.dto.request.ResetPasswordRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.LoginResponse;
import com.example.demo.dto.response.PasswordResetVerificationResponse;
import com.example.demo.dto.response.UserProfileResponse;
import com.example.demo.dto.response.UserResponse;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String AUTHORIZATION = "Bearer valid-token";

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void routesUserList() throws Exception {
        when(userService.findAllUsers()).thenReturn(ApiResponse.success("Users retrieved successfully", List.of()));

        mockMvc.perform(get("/usersall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        verify(userService).findAllUsers();
    }

    @Test
    void routesVendorAndOrganizerLocalRegistration() throws Exception {
        when(userService.registerLocal(any(LocalRegisterRequest.class), eq("VENDOR")))
                .thenReturn(ApiResponse.success("registered"));
        when(userService.registerLocal(any(LocalRegisterRequest.class), eq("ORGANIZER")))
                .thenReturn(ApiResponse.success("registered"));

        mockMvc.perform(post("/api/vendor/local-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
        mockMvc.perform(post("/api/organizer/local-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistrationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        verify(userService).registerLocal(any(LocalRegisterRequest.class), eq("VENDOR"));
        verify(userService).registerLocal(any(LocalRegisterRequest.class), eq("ORGANIZER"));
    }

    @Test
    void routesVendorAndOrganizerGoogleRegistration() throws Exception {
        when(userService.registerGoogle(any(GoogleCredentialRequest.class), eq("VENDOR")))
                .thenReturn(ApiResponse.success("registered"));
        when(userService.registerGoogle(any(GoogleCredentialRequest.class), eq("ORGANIZER")))
                .thenReturn(ApiResponse.success("registered"));

        mockMvc.perform(post("/api/vendor/google-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizer/google-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleJson()))
                .andExpect(status().isOk());

        verify(userService).registerGoogle(any(GoogleCredentialRequest.class), eq("VENDOR"));
        verify(userService).registerGoogle(any(GoogleCredentialRequest.class), eq("ORGANIZER"));
    }

    @Test
    void routesAllThreeLocalLoginPortalsWithExpectedRole() throws Exception {
        when(userService.loginLocal(any(LocalLoginRequest.class), any(String.class)))
                .thenReturn(ApiResponse.success("Login successful", (LoginResponse) null));

        mockMvc.perform(post("/api/vendor/local-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizer/local-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/local-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andExpect(status().isOk());

        verify(userService).loginLocal(any(LocalLoginRequest.class), eq("VENDOR"));
        verify(userService).loginLocal(any(LocalLoginRequest.class), eq("ORGANIZER"));
        verify(userService).loginLocal(any(LocalLoginRequest.class), eq("ADMIN"));
    }

    @Test
    void routesVendorAndOrganizerGoogleLogin() throws Exception {
        when(userService.loginGoogle(any(GoogleCredentialRequest.class), any(String.class)))
                .thenReturn(ApiResponse.success("Google login successful", (LoginResponse) null));

        mockMvc.perform(post("/api/vendor/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/organizer/google-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleJson()))
                .andExpect(status().isOk());

        verify(userService).loginGoogle(any(GoogleCredentialRequest.class), eq("VENDOR"));
        verify(userService).loginGoogle(any(GoogleCredentialRequest.class), eq("ORGANIZER"));
    }

    @Test
    void routesGoogleBindingAndAccountEmailVerification() throws Exception {
        when(userService.bindGoogle(eq(AUTHORIZATION), any(GoogleCredentialRequest.class)))
                .thenReturn(ApiResponse.success("bound"));
        when(userService.verifyCreateAccountEmail(any(EmailVerificationRequest.class)))
                .thenReturn(ApiResponse.success("verified"));

        mockMvc.perform(post("/api/auth/google-bind")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(googleJson()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/createAccount/emailVerify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson()))
                .andExpect(status().isOk());

        verify(userService).bindGoogle(eq(AUTHORIZATION), any(GoogleCredentialRequest.class));
        verify(userService).verifyCreateAccountEmail(any(EmailVerificationRequest.class));
    }

    @Test
    void routesCompletePasswordResetFlow() throws Exception {
        when(userService.requestPasswordReset(any(RequestPasswordResetRequest.class)))
                .thenReturn(ApiResponse.success("requested"));
        when(userService.verifyResetPasswordEmail(any(EmailVerificationRequest.class)))
                .thenReturn(ApiResponse.success(
                        "verified",
                        new PasswordResetVerificationResponse("reset-token")));
        when(userService.resetPassword(eq(AUTHORIZATION), any(ResetPasswordRequest.class)))
                .thenReturn(ApiResponse.success("reset"));

        mockMvc.perform(post("/api/auth/resetPassword/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"vendor1@example.test\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/resetPassword/emailVerify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resetToken").value("reset-token"));
        mockMvc.perform(post("/api/auth/resetPassword/reset")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"newPassword1\"}"))
                .andExpect(status().isOk());

        verify(userService).requestPasswordReset(any(RequestPasswordResetRequest.class));
        verify(userService).verifyResetPasswordEmail(any(EmailVerificationRequest.class));
        verify(userService).resetPassword(eq(AUTHORIZATION), any(ResetPasswordRequest.class));
    }

    @Test
    void routesLogoutCurrentUserAndAccountDeactivation() throws Exception {
        when(userService.logout(AUTHORIZATION)).thenReturn(ApiResponse.success("logout"));
        when(userService.getCurrentUser(AUTHORIZATION))
                .thenReturn(ApiResponse.success("current user", (UserProfileResponse) null));
        when(userService.deactivateCurrentAccount(AUTHORIZATION))
                .thenReturn(ApiResponse.success("deactivated"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/account/deactivate").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk());

        verify(userService).logout(AUTHORIZATION);
        verify(userService).getCurrentUser(AUTHORIZATION);
        verify(userService).deactivateCurrentAccount(AUTHORIZATION);
    }

    @Test
    void rejectsInvalidRegistrationBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/vendor/local-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"bad-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void rejectsInvalidVerificationCodeBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/createAccount/emailVerify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"vendor1@example.test\",\"code\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    private String validRegistrationJson() {
        return """
                {
                  "name": "Test User",
                  "email": "vendor1@example.test",
                  "password": "a12345678"
                }
                """;
    }

    private String validLoginJson() {
        return """
                {
                  "email": "vendor1@example.test",
                  "password": "a12345678"
                }
                """;
    }

    private String googleJson() {
        return "{\"credential\":\"google-credential\"}";
    }

    private String verificationJson() {
        return "{\"email\":\"vendor1@example.test\",\"code\":\"123456\"}";
    }
}
