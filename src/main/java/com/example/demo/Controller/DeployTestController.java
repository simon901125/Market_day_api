package com.example.demo.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeployTestController {

    @GetMapping("/api/deploy-test")
    public Map<String, String> deployTest() {
        return Map.of(
                "status", "ok",
                "version", "deploy-test-001",
                "message", "GitHub Actions auto deployment works",
                "serverTime", Instant.now().toString()
        );
    }
}