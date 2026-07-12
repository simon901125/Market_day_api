package com.example.demo.enums.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
     * 使用者帳號狀態，包含已刪除、已停用、活動中、未激活
     */
    @Getter
    @RequiredArgsConstructor
    public enum UserStatus {
        /** 已刪除 */
        IS_DELETED("isDelete", "已刪除"),
        /** 已停用 */
        DISABLED("disabled", "已停用"),
        /** 活動中 */
        ACTIVE("active", "活動中"),
        /** 未激活 */
        UNACTIVE("unactive", "未激活");

        @JsonValue
        private final String status;
        private final String description;

        @JsonCreator
        public static UserStatus fromStatus(String status) {
            for (UserStatus s : values()) {
                if (s.status.equals(status)) {
                    return s;
                }
            }
            return null;
        }

    }
