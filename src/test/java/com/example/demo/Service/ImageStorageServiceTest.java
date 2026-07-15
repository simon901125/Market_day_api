package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.demo.Repository.ImageStorageRepository;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.StoredImageResponse;

class ImageStorageServiceTest {

    private static final String AUTHORIZATION = "Bearer valid-token";

    @TempDir
    Path imageRoot;

    private JwtService jwtService;
    private ImageStorageRepository imageStorageRepository;
    private ImageStorageService imageStorageService;

    @BeforeEach
    void setUp() {
        jwtService = org.mockito.Mockito.mock(JwtService.class);
        imageStorageRepository = org.mockito.Mockito.mock(ImageStorageRepository.class);
        imageStorageService = new ImageStorageService(
                jwtService,
                imageStorageRepository,
                imageRoot.toString(),
                "https://api.example.test");
        when(jwtService.extractTokenFromAuthorizationHeader(AUTHORIZATION)).thenReturn("valid-token");
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.getEmail("valid-token")).thenReturn("vendor1@example.test");
    }

    @Test
    void productIsStoredDirectlyAndBoundToDatabase() throws Exception {
        when(jwtService.getRole("valid-token")).thenReturn("VENDOR");
        when(imageStorageRepository.updateProductImage(
                eq("vendor1@example.test"),
                eq(15L),
                anyString())).thenReturn(1);

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "PRODUCT",
                15L,
                null,
                png("product.png"));

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().productId()).isEqualTo(15L);
        assertThat(response.getData().eventId()).isNull();
        assertThat(response.getData().imageUrl())
                .startsWith("https://api.example.test/images/")
                .endsWith(".png");
        try (var files = Files.walk(imageRoot)) {
            assertThat(files.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    @Test
    void vendorAvatarIsStoredByIndependentImageApiAndBoundToDatabase() throws Exception {
        when(jwtService.getRole("valid-token")).thenReturn("VENDOR");
        when(imageStorageRepository.updateVendorImage(
                eq("vendor1@example.test"),
                eq("avatar_image_url"),
                anyString())).thenReturn(1);

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "VENDOR_AVATAR",
                null,
                null,
                png("avatar.png"));

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().imageUrl())
                .contains("/images/")
                .contains("/vendor-avatar/");
    }

    @Test
    void failedProductOwnershipBindingDoesNotLeaveImageFile() throws Exception {
        when(jwtService.getRole("valid-token")).thenReturn("VENDOR");

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "PRODUCT",
                999L,
                null,
                png("product.png"));

        assertThat(response.isSuccessStatus()).isFalse();
        try (var files = Files.walk(imageRoot)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void productRequiresProductId() throws Exception {
        when(jwtService.getRole("valid-token")).thenReturn("VENDOR");

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "PRODUCT",
                null,
                null,
                png("product.png"));

        assertThat(response.isSuccessStatus()).isFalse();
        try (var files = Files.list(imageRoot)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void vendorCannotStoreOrganizerEventImage() {
        when(jwtService.getRole("valid-token")).thenReturn("VENDOR");

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "EVENT_COVER",
                null,
                1L,
                png("cover.png"));

        assertThat(response.isSuccessStatus()).isFalse();
    }

    @Test
    void organizerCanStoreEventCoverImage() {
        when(jwtService.getRole("valid-token")).thenReturn("ORGANIZER");
        when(jwtService.getEmail("valid-token")).thenReturn("organizer1@example.test");
        when(imageStorageRepository.updateEventImage(
                eq("organizer1@example.test"),
                eq(10L),
                eq("cover_image_url"),
                anyString())).thenReturn(1);

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "EVENT_COVER",
                null,
                10L,
                png("event-cover.png"));

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().purpose()).isEqualTo("EVENT_COVER");
        assertThat(response.getData().productId()).isNull();
        assertThat(response.getData().eventId()).isEqualTo(10L);
        assertThat(response.getData().imageUrl()).contains("/event-cover/");
    }

    @Test
    void organizerCanStoreEventMapPdf() {
        when(jwtService.getRole("valid-token")).thenReturn("ORGANIZER");
        when(jwtService.getEmail("valid-token")).thenReturn("organizer1@example.test");
        when(imageStorageRepository.updateEventImage(
                eq("organizer1@example.test"),
                eq(10L),
                eq("map_image_url"),
                anyString())).thenReturn(1);
        MockMultipartFile pdf = new MockMultipartFile(
                "file",
                "map.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "EVENT_MAP",
                null,
                10L,
                pdf);

        assertThat(response.isSuccessStatus()).isTrue();
        assertThat(response.getData().imageUrl()).endsWith(".pdf");
    }

    @Test
    void eventImageRejectsProductIdInPlaceOfEventId() throws Exception {
        when(jwtService.getRole("valid-token")).thenReturn("ORGANIZER");

        ApiResponse<StoredImageResponse> response = imageStorageService.store(
                AUTHORIZATION,
                "EVENT_COVER",
                10L,
                null,
                png("event-cover.png"));

        assertThat(response.isSuccessStatus()).isFalse();
        assertThat(response.getMessage()).contains("eventId");
    }

    private MockMultipartFile png(String fileName) {
        return new MockMultipartFile(
                "file",
                fileName,
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01});
    }
}
