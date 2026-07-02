package com.km.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalFileStorageService 单元测试。
 * 覆盖：store、retrieve、delete、isAvailable、路径穿越防护。
 */
class LocalFileStorageServiceTest {

    private LocalFileStorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 通过系统属性注入临时目录，避免依赖真实路径
        System.setProperty("km.storage.local-path", tempDir.toString());
        storageService = new LocalFileStorageService();
        storageService.init();
    }

    // ====== 构造函数 & 初始化 ======

    @Test
    void shouldInitializeStorageDirectory() {
        assertTrue(Files.exists(tempDir));
        assertTrue(storageService.isAvailable());
    }

    @Test
    void shouldUseEnvVariablePath() {
        // 已通过系统属性设置，验证 basePath 正确
        Path testFile = tempDir.resolve("test.txt");
        storageService.store("test.txt",
                new ByteArrayInputStream("hello".getBytes()), 5, "text/plain");
        assertTrue(Files.exists(testFile));
    }

    // ====== store ======

    @Test
    void shouldStoreFileSuccessfully() throws IOException {
        byte[] content = "Hello, World!".getBytes();
        InputStream stream = new ByteArrayInputStream(content);

        storageService.store("docs/report.txt", stream, content.length, "text/plain");

        Path storedFile = tempDir.resolve("docs/report.txt");
        assertTrue(Files.exists(storedFile));
        assertEquals("Hello, World!", Files.readString(storedFile));
    }

    @Test
    void shouldOverwriteExistingFile() throws IOException {
        Path targetPath = tempDir.resolve("data.txt");
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, "old content");

        storageService.store("data.txt",
                new ByteArrayInputStream("new content".getBytes()), 11, "text/plain");

        assertEquals("new content", Files.readString(targetPath));
    }

    @Test
    void shouldStoreEmptyFile() throws IOException {
        storageService.store("empty.txt",
                new ByteArrayInputStream(new byte[0]), 0, "text/plain");

        Path storedFile = tempDir.resolve("empty.txt");
        assertTrue(Files.exists(storedFile));
        assertEquals(0, Files.size(storedFile));
    }

    @Test
    void shouldCreateParentDirectories() {
        storageService.store("a/b/c/d/file.txt",
                new ByteArrayInputStream("deep".getBytes()), 4, "text/plain");

        assertTrue(Files.exists(tempDir.resolve("a/b/c/d/file.txt")));
    }

    // ====== retrieve ======

    @Test
    void shouldRetrieveStoredFile() throws IOException {
        byte[] content = "retrieve test content".getBytes();
        storageService.store("readable.txt",
                new ByteArrayInputStream(content), content.length, "text/plain");

        InputStream retrieved = storageService.retrieve("readable.txt");
        byte[] result = retrieved.readAllBytes();

        assertArrayEquals(content, result);
    }

    @Test
    void shouldThrowExceptionWhenRetrieveNonExistentFile() {
        assertThrows(RuntimeException.class,
                () -> storageService.retrieve("nonexistent.txt"));
    }

    // ====== delete ======

    @Test
    void shouldDeleteExistingFile() throws IOException {
        Path targetPath = tempDir.resolve("to-delete.txt");
        Files.createDirectories(targetPath.getParent());
        Files.writeString(targetPath, "delete me");

        storageService.delete("to-delete.txt");

        assertFalse(Files.exists(targetPath));
    }

    @Test
    void shouldNotThrowWhenDeleteNonExistentFile() {
        assertDoesNotThrow(() -> storageService.delete("nonexistent.txt"));
    }

    // ====== isAvailable ======

    @Test
    void shouldReturnTrueWhenDirectoryExistsAndWritable() {
        assertTrue(storageService.isAvailable());
    }

    // ====== 路径穿越防护 ======

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(SecurityException.class,
                () -> storageService.store("../etc/passwd",
                        new ByteArrayInputStream("hack".getBytes()), 4, "text/plain"));
    }

    @Test
    void shouldRejectAbsolutePathInName() {
        // resolve 会检测路径是否在 basePath 之外
        assertThrows(SecurityException.class,
                () -> storageService.store("/etc/passwd",
                        new ByteArrayInputStream("hack".getBytes()), 4, "text/plain"));
    }

    @Test
    void shouldRejectComplexPathTraversal() {
        assertThrows(SecurityException.class,
                () -> storageService.store("foo/../../bar/../../../etc/passwd",
                        new ByteArrayInputStream("hack".getBytes()), 4, "text/plain"));
    }
}
