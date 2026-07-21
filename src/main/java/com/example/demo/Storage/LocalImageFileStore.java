package com.example.demo.Storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "local", matchIfMissing = true)
public class LocalImageFileStore implements ImageFileStore {

    private final Path root;
    private final String publicBaseUrl;

    public LocalImageFileStore(
            @Value("${app.image.directory:images}") String directory,
            @Value("${app.image.public-base-url:}") String publicBaseUrl) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @Override
    public String save(String objectName, String contentType, InputStream inputStream) throws IOException {
        Path target = root.resolve(objectName).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid image object name");
        }
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);

        String relativeUrl = "/images/" + objectName;
        if (!publicBaseUrl.isBlank()) {
            return publicBaseUrl + relativeUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(relativeUrl).toUriString();
    }

    @Override
    public void delete(String objectName) throws IOException {
        Path target = root.resolve(objectName).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid image object name");
        }
        Files.deleteIfExists(target);
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
