package com.example.demo.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CredentialEncryptionService {
    private static final String VERSION = "v1";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    private final String masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialEncryptionService(
            @Value("${PAYMENT_CREDENTIAL_ENCRYPTION_KEY:}") String masterKey) {
        this.masterKey = masterKey == null ? "" : masterKey.trim();
    }

    public String encrypt(String plainText) {
        requireConfigured();
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("藍新金流金鑰不可為空");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("藍新金流金鑰加密失敗", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        requireConfigured();
        try {
            String[] parts = encryptedValue == null ? new String[0] : encryptedValue.split(":", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("不支援的藍新金流金鑰格式");
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("藍新金流金鑰解密失敗", exception);
        }
    }

    private SecretKeySpec encryptionKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private void requireConfigured() {
        if (masterKey.length() < 32) {
            throw new IllegalStateException(
                    "系統尚未正確設定 PAYMENT_CREDENTIAL_ENCRYPTION_KEY（至少 32 個字元），請聯絡系統管理員");
        }
    }
}
