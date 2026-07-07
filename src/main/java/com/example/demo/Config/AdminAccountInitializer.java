package com.example.demo.Config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.demo.Repository.UserRepository;
import com.example.demo.Service.AuthService;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final AuthService authService;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    public AdminAccountInitializer(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            logger.info("Default admin account is not configured because ADMIN_EMAIL is empty.");
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            logger.warn("Default admin account was not created because ADMIN_PASSWORD is empty.");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            Map<String, Object> existingUser = userRepository.findProfileByEmail(adminEmail).orElse(null);
            if (existingUser != null && !"ADMIN".equals(existingUser.get("role"))) {
                logger.warn(
                        "Default admin account was not created because {} already belongs to role {}.",
                        adminEmail,
                        existingUser.get("role"));
                return;
            }

            logger.info("Default admin account already exists: {}", adminEmail);
            return;
        }

        userRepository.createSystemAdmin(
                adminEmail,
                authService.hashPassword(adminPassword));
        logger.info("Default admin account created: {}", adminEmail);
    }
}
