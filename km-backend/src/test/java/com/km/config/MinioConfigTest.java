package com.km.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MinioConfig 单元测试。
 * 覆盖：MinioClient bean 创建、配置属性绑定。
 */
class MinioConfigTest {

    private MinioConfig minioConfig;

    @BeforeEach
    void setUp() {
        minioConfig = new MinioConfig();
        minioConfig.setEndpoint("http://localhost:9000");
        minioConfig.setAccessKey("minioadmin");
        minioConfig.setSecretKey("minioadmin");
        minioConfig.setBucket("km-documents");
    }

    @Test
    void shouldCreateMinioClient() {
        MinioClient client = minioConfig.minioClient();
        assertNotNull(client);
    }

    @Test
    void shouldBindProperties() {
        assertEquals("http://localhost:9000", minioConfig.getEndpoint());
        assertEquals("minioadmin", minioConfig.getAccessKey());
        assertEquals("minioadmin", minioConfig.getSecretKey());
        assertEquals("km-documents", minioConfig.getBucket());
    }

    @Test
    void shouldCreateClientWithCustomEndpoint() {
        minioConfig.setEndpoint("https://minio.example.com");
        minioConfig.setAccessKey("custom-key");
        minioConfig.setSecretKey("custom-secret");

        MinioClient client = minioConfig.minioClient();
        assertNotNull(client);
    }
}
