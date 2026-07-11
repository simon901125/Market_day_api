package com.example.demo.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 使用者角色，包含管理員、主辦方、攤主
 */
@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN("admin", "管理員"),
    ORGANIZER("organizer", "主辦方"),
    VENDOR("vender", "攤主");

    @JsonValue
    private final String role;
    private final String description;

    @JsonCreator
    public static Role fromRole(String role){
        for (Role r : values()) {
            if (r.role.equals(role)) {
                return r;
            }
        }
        throw new IllegalArgumentException("未知的角色: " + role);
    }
}
