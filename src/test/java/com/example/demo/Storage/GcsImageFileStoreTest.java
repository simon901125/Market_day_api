package com.example.demo.Storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

class GcsImageFileStoreTest {

    @Test
    void uploadsToConfiguredBucketAndReturnsPublicUrl() throws Exception {
        Storage storage = org.mockito.Mockito.mock(Storage.class);
        GcsImageFileStore fileStore = new GcsImageFileStore(
                storage,
                "market-day-images",
                "https://storage.googleapis.com/");

        String url = fileStore.save(
                "account/product/image.png",
                "image/png",
                new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertThat(url).isEqualTo(
                "https://storage.googleapis.com/market-day-images/account/product/image.png");
        verify(storage).createFrom(
                any(BlobInfo.class),
                any(ByteArrayInputStream.class),
                any(Storage.BlobWriteOption.class));
    }

    @Test
    void deletesObjectFromConfiguredBucket() throws Exception {
        Storage storage = org.mockito.Mockito.mock(Storage.class);
        GcsImageFileStore fileStore = new GcsImageFileStore(
                storage,
                "market-day-images",
                "https://storage.googleapis.com");

        fileStore.delete("account/product/image.png");

        verify(storage).delete(eq(BlobId.of("market-day-images", "account/product/image.png")));
    }
}
