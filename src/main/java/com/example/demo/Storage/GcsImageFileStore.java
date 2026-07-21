package com.example.demo.Storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "gcs")
public class GcsImageFileStore implements ImageFileStore {

    private final Storage storage;
    private final String bucket;
    private final String publicBaseUrl;

    @Autowired
    public GcsImageFileStore(
            @Value("${app.image.gcs.bucket}") String bucket,
            @Value("${app.image.gcs.public-base-url:https://storage.googleapis.com}") String publicBaseUrl) {
        this(StorageOptions.getDefaultInstance().getService(), bucket, publicBaseUrl);
    }

    GcsImageFileStore(Storage storage, String bucket, String publicBaseUrl) {
        this.storage = storage;
        this.bucket = requireText(bucket, "GCS bucket name is required");
        this.publicBaseUrl = trimTrailingSlash(requireText(publicBaseUrl, "GCS public base URL is required"));
    }

    @Override
    public String save(String objectName, String contentType, InputStream inputStream) throws IOException {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectName))
                .setContentType(contentType)
                .setCacheControl("public, max-age=31536000, immutable")
                .build();
        storage.createFrom(blobInfo, inputStream, Storage.BlobWriteOption.doesNotExist());
        return publicBaseUrl + "/" + bucket + "/" + objectName;
    }

    @Override
    public void delete(String objectName) {
        storage.delete(BlobId.of(bucket, objectName));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
