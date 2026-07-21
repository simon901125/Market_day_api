package com.example.demo.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.Repository.ImageStorageRepository;
import com.example.demo.Storage.ImageFileStore;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.StoredImageResponse;

@Service
public class ImageStorageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D};

    private final JwtService jwtService;
    private final ImageStorageRepository imageStorageRepository;
    private final ImageFileStore imageFileStore;

    public ImageStorageService(
            JwtService jwtService,
            ImageStorageRepository imageStorageRepository,
            ImageFileStore imageFileStore) {
        this.jwtService = jwtService;
        this.imageStorageRepository = imageStorageRepository;
        this.imageFileStore = imageFileStore;
    }

    @Transactional
    public ApiResponse<StoredImageResponse> store(
            String authorizationHeader,
            String purposeValue,
            Long productId,
            Long eventId,
            MultipartFile file) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank()) {
            return ApiResponse.fail("Authorization token is required");
        }
        if (!jwtService.isTokenValid(token)) {
            return ApiResponse.fail("Invalid or expired token");
        }

        ImagePurpose purpose = ImagePurpose.from(purposeValue);
        if (purposeValue == null || purposeValue.isBlank()) {
            return ApiResponse.fail("Image purpose is required");
        }
        if (purpose == null) {
            return ApiResponse.fail("Image purpose is invalid");
        }
        String role = jwtService.getRole(token);
        if (!purpose.allowedRoles().contains(role)) {
            return ApiResponse.fail("Image purpose is not allowed for this account");
        }
        String identifierValidationError = validateIdentifiers(purpose, productId, eventId);
        if (identifierValidationError != null) {
            return ApiResponse.fail(identifierValidationError);
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail("Image file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ApiResponse.fail("File size must not exceed 5 MB");
        }

        StoredFileType fileType;
        try {
            fileType = detectFileType(file);
        } catch (IOException exception) {
            return ApiResponse.fail("File upload failed");
        }
        if (fileType == null || !purpose.allowedTypes().contains(fileType)) {
            return ApiResponse.fail("Only JPG or PNG files are allowed");
        }

        String email = jwtService.getEmail(token);
        String fileName = UUID.randomUUID() + "." + fileType.extension();
        String accountKey = accountKey(email);
        String objectName = accountKey + "/" + purpose.directory() + "/" + fileName;
        String imageUrl;
        boolean stored = false;

        try {
            try (InputStream inputStream = file.getInputStream()) {
                imageUrl = imageFileStore.save(objectName, fileType.contentType(), inputStream);
                stored = true;
            }
            int updatedRows = bindImageToDatabase(email, purpose, productId, eventId, imageUrl);
            if (updatedRows != 1) {
                imageFileStore.delete(objectName);
                markTransactionRollbackOnly();
                return ApiResponse.fail("Image target not found or does not belong to this account");
            }
        } catch (IOException | RuntimeException exception) {
            if (stored) {
                deleteQuietly(objectName);
            }
            markTransactionRollbackOnly();
            return ApiResponse.fail("Image save failed");
        }

        return ApiResponse.success(
                "Image saved successfully",
                new StoredImageResponse(
                        purpose.name(),
                        productId,
                        eventId,
                        imageUrl,
                        fileType.contentType(),
                file.getSize()));
    }

    private String validateIdentifiers(ImagePurpose purpose, Long productId, Long eventId) {
        return switch (purpose.targetType()) {
            case NONE -> productId != null
                    ? "Product id is not allowed for this image purpose"
                    : eventId != null ? "Event id is not allowed for this image purpose" : null;
            case PRODUCT -> productId == null || productId <= 0
                    ? "Product id is required for product image"
                    : eventId != null ? "Event id is not allowed for this image purpose" : null;
            case EVENT -> eventId == null || eventId <= 0
                    ? "Event id is required for event image"
                    : productId != null ? "Product id is not allowed for this image purpose" : null;
        };
    }

    private int bindImageToDatabase(
            String email,
            ImagePurpose purpose,
            Long productId,
            Long eventId,
            String imageUrl) {
        return switch (purpose) {
            case VENDOR_AVATAR -> imageStorageRepository.updateVendorImage(email, "avatar_image_url", imageUrl);
            case VENDOR_COVER -> imageStorageRepository.updateVendorImage(email, "cover_image_url", imageUrl);
            case PRODUCT -> imageStorageRepository.updateProductImage(email, productId, imageUrl);
            case EVENT_COVER -> imageStorageRepository.updateEventImage(email, eventId, "cover_image_url", imageUrl);
            case EVENT_MAP -> imageStorageRepository.updateEventImage(email, eventId, "map_image_url", imageUrl);
        };
    }

    private StoredFileType detectFileType(MultipartFile file) throws IOException {
        byte[] header = new byte[8];
        int length;
        try (InputStream inputStream = file.getInputStream()) {
            length = inputStream.read(header);
        }
        if (startsWith(header, length, JPEG_SIGNATURE)) {
            return StoredFileType.JPEG;
        }
        if (startsWith(header, length, PNG_SIGNATURE)) {
            return StoredFileType.PNG;
        }
        if (startsWith(header, length, PDF_SIGNATURE)) {
            return StoredFileType.PDF;
        }
        return null;
    }

    private boolean startsWith(byte[] source, int sourceLength, byte[] signature) {
        return sourceLength >= signature.length
                && Arrays.equals(Arrays.copyOf(source, signature.length), signature);
    }

    private String accountKey(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 12; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void deleteQuietly(String objectName) {
        try {
            imageFileStore.delete(objectName);
        } catch (IOException ignored) {
            // Failed saves must not replace the database URL; leftover files can be inspected manually.
        }
    }

    private void markTransactionRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (RuntimeException ignored) {
            // Unit tests may call the service without a Spring transaction.
        }
    }

    private enum ImagePurpose {
        VENDOR_AVATAR("vendor-avatar", Set.of(StoredFileType.JPEG, StoredFileType.PNG), Set.of("VENDOR"), TargetType.NONE),
        VENDOR_COVER("vendor-cover", Set.of(StoredFileType.JPEG, StoredFileType.PNG), Set.of("VENDOR"), TargetType.NONE),
        PRODUCT("product", Set.of(StoredFileType.JPEG, StoredFileType.PNG), Set.of("VENDOR"), TargetType.PRODUCT),
        EVENT_COVER("event-cover", Set.of(StoredFileType.JPEG, StoredFileType.PNG), Set.of("ORGANIZER"), TargetType.EVENT),
        EVENT_MAP("event-map", Set.of(StoredFileType.JPEG, StoredFileType.PNG), Set.of("ORGANIZER"), TargetType.EVENT);

        private final String directory;
        private final Set<StoredFileType> allowedTypes;
        private final Set<String> allowedRoles;
        private final TargetType targetType;

        ImagePurpose(String directory, Set<StoredFileType> allowedTypes, Set<String> allowedRoles, TargetType targetType) {
            this.directory = directory;
            this.allowedTypes = allowedTypes;
            this.allowedRoles = allowedRoles;
            this.targetType = targetType;
        }

        private String directory() {
            return directory;
        }

        private Set<StoredFileType> allowedTypes() {
            return allowedTypes;
        }

        private Set<String> allowedRoles() {
            return allowedRoles;
        }

        private TargetType targetType() {
            return targetType;
        }

        private static ImagePurpose from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                String normalizedValue = value
                        .trim()
                        .replace('-', '_')
                        .toUpperCase(Locale.ROOT);
                return valueOf(normalizedValue);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    private enum TargetType {
        NONE,
        PRODUCT,
        EVENT
    }

    private enum StoredFileType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        PDF("pdf", "application/pdf");

        private final String extension;
        private final String contentType;

        StoredFileType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        private String extension() {
            return extension;
        }

        private String contentType() {
            return contentType;
        }
    }

}
