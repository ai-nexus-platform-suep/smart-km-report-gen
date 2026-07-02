package com.km.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local file system storage implementation (dev mode, no MinIO needed).
 * Files are stored under ${km.storage.local-path}/{objectName}.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "km.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path basePath;

    public LocalFileStorageService() {
        String path = System.getProperty("km.storage.local-path", "D:\\kb\\uploads");
        String envPath = System.getenv("KM_STORAGE_LOCAL_PATH");
        if (envPath != null && !envPath.isEmpty()) {
            path = envPath;
        }
        this.basePath = Paths.get(path).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(basePath);
            log.info("Local file storage initialized at: {}", basePath);
        } catch (IOException e) {
            log.warn("Failed to create storage directory: {}", basePath, e);
        }
    }

    @Override
    public void store(String objectName, InputStream stream, long size, String contentType) {
        Path targetPath = resolve(objectName);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(stream, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.debug("File stored locally: {}", targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Local file store failed: " + targetPath, e);
        }
    }

    @Override
    public InputStream retrieve(String objectName) {
        Path targetPath = resolve(objectName);
        try {
            return Files.newInputStream(targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Local file read failed: " + targetPath, e);
        }
    }

    @Override
    public void delete(String objectName) {
        Path targetPath = resolve(objectName);
        try {
            Files.deleteIfExists(targetPath);
            log.debug("File deleted locally: {}", targetPath);
        } catch (IOException e) {
            log.warn("Local file delete failed: {}", targetPath, e);
        }
    }

    @Override
    public boolean isAvailable() {
        return Files.exists(basePath) && Files.isWritable(basePath);
    }

    private Path resolve(String objectName) {
        Path resolved = basePath.resolve(objectName).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("Illegal file path: " + objectName);
        }
        return resolved;
    }
}
