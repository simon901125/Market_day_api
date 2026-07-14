package com.example.demo.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.demo.dto.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), "File size must not exceed 5 MB"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String validationMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .distinct()
                .reduce((first, second) -> first + "; " + second)
                .map(message -> "資料驗證失敗：" + message)
                .orElse("資料驗證失敗");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), validationMessage));
    }

    private String formatFieldError(FieldError fieldError) {
        return toChineseFieldName(fieldError.getField()) + "：" + toChineseValidationMessage(fieldError.getDefaultMessage());
    }

    private String toChineseFieldName(String field) {
        return switch (field) {
            case "name" -> "姓名";
            case "email" -> "電子信箱";
            case "password" -> "密碼";
            case "resetToken" -> "重設密碼 token";
            case "phone" -> "手機號碼";
            case "code" -> "驗證碼";
            case "applicationNo" -> "報名編號";
            case "stallNo" -> "攤位編號";
            default -> field;
        };
    }

    private String toChineseValidationMessage(String message) {
        if (message == null) {
            return "欄位格式不正確";
        }
        return switch (message) {
            case "Name is required" -> "姓名為必填";
            case "Name must not exceed 20 characters" -> "姓名不可超過 20 個字元";
            case "Email is required" -> "電子信箱為必填";
            case "Invalid email format" -> "電子信箱格式不正確";
            case "Password is required" -> "密碼為必填";
            case "Reset token is required" -> "重設密碼 token 為必填";
            case "Password must be at least 8 characters and contain letters and numbers" -> "密碼至少 8 個字元，且需包含英文與數字";
            case "Phone is required" -> "手機號碼為必填";
            case "Phone must be 10 digits and start with 09" -> "手機號碼需為 10 碼且以 09 開頭";
            case "Verification code is required" -> "驗證碼為必填";
            case "Verification code must be 6 digits" -> "驗證碼需為 6 位數字";
            case "Application number is required" -> "報名編號為必填";
            case "Application number must match MD plus 3 digits, e.g. MD001" -> "報名編號格式需為 MD 加 3 位數字，例如 MD001";
            case "Stall number is required" -> "攤位編號為必填";
            case "Stall number must match an uppercase letter plus 2 digits, e.g. A01" -> "攤位編號格式需為大寫英文字母加 2 位數字，例如 A01";
            default -> message;
        };
    }
}
