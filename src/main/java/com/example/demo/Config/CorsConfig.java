package com.example.demo.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/api/**")
                .allowedOrigins(
                        "https://market-day-web.pages.dev",
                        "https://market-day-1hp.pages.dev",
                        "https://ccore.newebpay.com",
                        "http://localhost:4200"
                )
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
                .allowedHeaders(
                        "Content-Type",
                        "Authorization",
                        "Accept"
                )
                .maxAge(3600);

        registry.addMapping("/images/**")
                .allowedOrigins(
                        "http://localhost:4200",
                        "https://market-day-web.pages.dev",
                        "https://market-day-1hp.pages.dev"
                )
                .allowedMethods(
                        "GET",
                        "OPTIONS"
                )
                .maxAge(3600);
    }
}