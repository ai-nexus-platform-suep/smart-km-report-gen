package com.km.storage;

import com.km.config.MinioConfig;
import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MinioFileStorageService 单元测试。
 * 覆盖：store、retrieve、delete、isAvailable、bucket 初始化。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinioFileStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    private MinioFileStorageService storageService;

    private static final String BUCKET = "km-documents";
    private static final String ENDPOINT = "http://localhost:9000";

    @BeforeEach
    void setUp() {
        when(minioConfig.getBucket()).thenReturn(BUCKET);
        when(minioConfig.getEndpoint()).thenReturn(ENDPOINT);
        storageService = new MinioFileStorageService(minioClient, minioConfig);
    }

    // ====== init / ensureBucket ======

    @Test
    void shouldCreateBucketWhenNotExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storageService.init();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void shouldSkipBucketCreationWhenExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        storageService.init();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void shouldHandleBucketInitFailure() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // init 内部捕获异常，不应向外抛出
        assertDoesNotThrow(() -> storageService.init());
    }

    // ====== store ======

    @Test
    void shouldStoreFileSuccessfully() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        byte[] content = "test content".getBytes();
        InputStream stream = new ByteArrayInputStream(content);

        storageService.store("docs/report.pdf", stream, content.length, "application/pdf");

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldThrowExceptionWhenStoreFails() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        doThrow(new RuntimeException("MinIO unavailable"))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        assertThrows(RuntimeException.class,
                () -> storageService.store("doc.pdf",
                        new ByteArrayInputStream("data".getBytes()), 4, "application/pdf"));
    }

    @Test
    void shouldEnsureBucketBeforeStore() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storageService.store("doc.pdf",
                new ByteArrayInputStream("data".getBytes()), 4, "application/pdf");

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    // ====== retrieve ======

    @Test
    void shouldRetrieveFileSuccessfully() throws Exception {
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        doReturn(mockResponse).when(minioClient).getObject(any(GetObjectArgs.class));

        InputStream result = storageService.retrieve("docs/report.pdf");

        assertNotNull(result);
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void shouldThrowExceptionWhenRetrieveFails() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("Object not found"));

        assertThrows(RuntimeException.class,
                () -> storageService.retrieve("nonexistent.pdf"));
    }

    // ====== delete ======

    @Test
    void shouldDeleteFileSuccessfully() throws Exception {
        storageService.delete("docs/obsolete.pdf");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void shouldNotThrowWhenDeleteFails() throws Exception {
        doThrow(new RuntimeException("MinIO unavailable"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertDoesNotThrow(() -> storageService.delete("doc.pdf"));
    }

    // ====== isAvailable ======

    @Test
    void shouldReturnTrueWhenAvailable() {
        assertTrue(storageService.isAvailable());
    }
}
