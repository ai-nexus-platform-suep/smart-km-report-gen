package com.powerreport.admin.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final TemplateStorageProperties templateStorageProperties;
    private final AssetStorageProperties assetStorageProperties;

    @Bean
    @Primary
    public MinioClient minioClient() {
        TemplateStorageProperties.Minio minio = templateStorageProperties.getMinio();
        return buildClient(minio.getEndpoint(), minio.getAccessKey(), minio.getSecretKey());
    }

    @Bean("assetMinioClient")
    public MinioClient assetMinioClient() {
        AssetStorageProperties.Minio minio = assetStorageProperties.getMinio();
        return buildClient(minio.getEndpoint(), minio.getAccessKey(), minio.getSecretKey());
    }

    private MinioClient buildClient(String endpoint, String accessKey, String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
