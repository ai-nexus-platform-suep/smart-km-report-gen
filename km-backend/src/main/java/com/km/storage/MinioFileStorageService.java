package com.km.storage;

import com.km.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;

/**
 * MinIO 对象存储实现（生产环境使用）
 *
 * 由配置 km.storage.type=minio 激活
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "km.storage.type", havingValue = "minio")
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            ensureBucket();
            log.info("MinIO file storage initialized, endpoint={}, bucket={}",
                    minioConfig.getEndpoint(), minioConfig.getBucket());
        } catch (RuntimeException e) {
            log.warn("MinIO bucket initialization skipped, endpoint={}, bucket={}. Uploads will fail until MinIO is available.",
                    minioConfig.getEndpoint(), minioConfig.getBucket(), e);
        }
    }

    @Override
    public void store(String objectName, InputStream stream, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO存储失败: " + objectName, e);
        }
    }

    @Override
    public InputStream retrieve(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO读取失败: " + objectName, e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO删除失败: {}", objectName, e);
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("MinIO bucket initialization failed: " + minioConfig.getBucket(), e);
        }
    }
}
