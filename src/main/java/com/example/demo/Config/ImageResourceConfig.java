package com.example.demo.Config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ImageResourceConfig implements WebMvcConfigurer {

    private final Path imageStorageRoot;

    public ImageResourceConfig(@Value("${app.image.directory:images}") String imageDirectory) {
        this.imageStorageRoot = Path.of(imageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations(resourceLocation(imageStorageRoot));
    }

    private String resourceLocation(Path directory) {
        String location = directory.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
