package com.powerreport.admin.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.admin.config.TemplateStorageProperties;
import com.powerreport.admin.dto.TemplateConfigRequest;
import com.powerreport.admin.dto.TemplateFileResource;
import com.powerreport.admin.dto.TemplatePageResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateUpdateRequest;
import com.powerreport.admin.service.TemplateService;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportTemplateMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_VERSION = "1.0.0";

    private final ReportTemplateMapper templateMapper;
    private final TemplateStorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Override
    public TemplatePageResponse list(Integer page, Integer size, String reportType, Boolean enabled, String keyword) {
        int actualPage = normalizePage(page);
        int actualSize = normalizeSize(size);
        LambdaQueryWrapper<ReportTemplateEntity> query = new LambdaQueryWrapper<ReportTemplateEntity>()
                .eq(StringUtils.hasText(reportType), ReportTemplateEntity::getReportType, normalizeReportTypeOrNull(reportType))
                .eq(enabled != null, ReportTemplateEntity::getEnabled, enabled)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(ReportTemplateEntity::getName, keyword)
                        .or()
                        .like(ReportTemplateEntity::getVersion, keyword))
                .orderByDesc(ReportTemplateEntity::getUpdatedAt)
                .orderByDesc(ReportTemplateEntity::getCreatedAt);

        Page<ReportTemplateEntity> result = templateMapper.selectPage(Page.of(actualPage, actualSize), query);

        TemplatePageResponse response = new TemplatePageResponse();
        response.setTotal(result.getTotal());
        response.setPage(actualPage);
        response.setSize(actualSize);
        response.setRecords(result.getRecords().stream().map(this::toResponse).toList());
        return response;
    }

    @Override
    public TemplateResponse upload(MultipartFile file, String name, String reportType, String version,
                                   String configJson, Boolean enabled, String username) {
        validateTemplateName(name);
        String normalizedType = normalizeReportType(reportType);
        String normalizedVersion = normalizeVersion(version);
        validateConfigJson(configJson);
        StoredTemplateFile storedFile = storeFile(file, normalizedType);

        ReportTemplateEntity entity = new ReportTemplateEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name.trim());
        entity.setReportType(normalizedType);
        entity.setVersion(normalizedVersion);
        applyStoredFile(entity, storedFile);
        entity.setConfigJson(StringUtils.hasText(configJson) ? configJson : null);
        entity.setEnabled(enabled == null || enabled);
        entity.setCreatedBy(StringUtils.hasText(username) ? username : "local_user");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.insert(entity);
        return toResponse(entity);
    }

    @Override
    public TemplateResponse detail(String templateId) {
        return toResponse(requireTemplate(templateId));
    }

    @Override
    public TemplateResponse update(String templateId, TemplateUpdateRequest request) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getReportType())) {
            entity.setReportType(normalizeReportType(request.getReportType()));
        }
        if (StringUtils.hasText(request.getVersion())) {
            entity.setVersion(normalizeVersion(request.getVersion()));
        }
        if (request.getConfigJson() != null) {
            validateConfigJson(request.getConfigJson());
            entity.setConfigJson(StringUtils.hasText(request.getConfigJson()) ? request.getConfigJson() : null);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    public TemplateResponse replaceFile(String templateId, MultipartFile file) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        StoredTemplateFile oldFile = toStoredFile(entity);
        applyStoredFile(entity, storeFile(file, entity.getReportType()));
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(entity);
        deleteStoredFileQuietly(oldFile);
        return toResponse(entity);
    }

    @Override
    public String getConfig(String templateId) {
        return Objects.toString(requireTemplate(templateId).getConfigJson(), "");
    }

    @Override
    public TemplateResponse updateConfig(String templateId, TemplateConfigRequest request) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        validateConfigJson(request.getConfigJson());
        entity.setConfigJson(StringUtils.hasText(request.getConfigJson()) ? request.getConfigJson() : null);
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    public TemplateFileResource loadFile(String templateId) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        StoredTemplateFile storedFile = toStoredFile(entity);
        return isMinioStorage(storedFile.storageType())
                ? loadMinioFile(storedFile)
                : loadLocalFile(storedFile);
    }

    @Override
    public void delete(String templateId) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        templateMapper.deleteById(templateId);
        deleteStoredFileQuietly(toStoredFile(entity));
    }

    private ReportTemplateEntity requireTemplate(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            throw new IllegalArgumentException("templateId is required");
        }
        ReportTemplateEntity entity = templateMapper.selectById(templateId);
        if (entity == null) {
            throw new IllegalArgumentException("template does not exist");
        }
        return entity;
    }

    private StoredTemplateFile storeFile(MultipartFile file, String reportType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("template file is required");
        }
        String originalFileName = cleanOriginalFileName(file);
        return isMinioStorage(storageProperties.getStorageType())
                ? storeMinioFile(file, originalFileName, reportType)
                : storeLocalFile(file, originalFileName);
    }

    private StoredTemplateFile storeMinioFile(MultipartFile file, String originalFileName, String reportType) {
        TemplateStorageProperties.Minio minio = storageProperties.getMinio();
        String bucketName = minio.getBucketName();
        String objectName = buildObjectName(minio.getObjectPrefix(), reportType);
        String contentType = normalizeContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            ensureBucket(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new StoredTemplateFile(
                    "MINIO",
                    buildMinioPath(bucketName, objectName),
                    bucketName,
                    objectName,
                    originalFileName,
                    contentType,
                    file.getSize()
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to store template file to MinIO: " + e.getMessage());
        }
    }

    private StoredTemplateFile storeLocalFile(MultipartFile file, String originalFileName) {
        try {
            Path root = storageRoot();
            Files.createDirectories(root);
            String storedFileName = UUID.randomUUID() + ".docx";
            Path target = root.resolve(storedFileName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("invalid template storage path");
            }
            file.transferTo(target);
            return new StoredTemplateFile(
                    "LOCAL",
                    target.toString(),
                    null,
                    null,
                    originalFileName,
                    normalizeContentType(file.getContentType()),
                    Files.size(target)
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to store template file");
        }
    }

    private String cleanOriginalFileName(MultipartFile file) {
        String originalFileName = org.springframework.util.StringUtils.cleanPath(
                Objects.toString(file.getOriginalFilename(), "template.docx")
        );
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("invalid template file name");
        }
        String lowerName = originalFileName.toLowerCase();
        if (!lowerName.endsWith(".docx")) {
            throw new IllegalArgumentException("only .docx template files are supported");
        }
        return originalFileName;
    }

    private TemplateFileResource loadMinioFile(StoredTemplateFile storedFile) {
        if (!StringUtils.hasText(storedFile.bucketName()) || !StringUtils.hasText(storedFile.objectName())) {
            throw new IllegalArgumentException("template MinIO object is empty");
        }
        try {
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(storedFile.bucketName())
                    .object(storedFile.objectName())
                    .build());
            Resource resource = new InputStreamResource(inputStream);
            return new TemplateFileResource(
                    storedFile.downloadFileName(),
                    resource,
                    Objects.requireNonNullElse(storedFile.fileSize(), -1L),
                    storedFile.safeContentType()
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to load template file from MinIO: " + e.getMessage());
        }
    }

    private TemplateFileResource loadLocalFile(StoredTemplateFile storedFile) {
        Path path = resolveStoredPath(storedFile.filePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("template file does not exist");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            String fileName = StringUtils.hasText(storedFile.originalFileName())
                    ? storedFile.originalFileName()
                    : path.getFileName().toString();
            return new TemplateFileResource(fileName, resource, Files.size(path), storedFile.safeContentType());
        } catch (IOException e) {
            throw new IllegalStateException("failed to load template file");
        }
    }

    private void ensureBucket(String bucketName) throws Exception {
        if (!storageProperties.getMinio().isAutoCreateBucket()) {
            return;
        }
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        }
    }

    private String buildObjectName(String prefix, String reportType) {
        String normalizedPrefix = StringUtils.hasText(prefix) ? trimSlashes(prefix.trim()) : "templates";
        String normalizedType = StringUtils.hasText(reportType) ? reportType.trim() : "UNKNOWN";
        return normalizedPrefix + "/" + normalizedType + "/" + UUID.randomUUID() + ".docx";
    }

    private String trimSlashes(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String buildMinioPath(String bucketName, String objectName) {
        return "minio://" + bucketName + "/" + objectName;
    }

    private Path storageRoot() {
        return Paths.get(storageProperties.getStorageDir()).toAbsolutePath().normalize();
    }

    private Path resolveStoredPath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("template file path is empty");
        }
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return storageRoot().resolve(filePath).normalize();
    }

    private void deleteStoredFileQuietly(StoredTemplateFile storedFile) {
        if (storedFile == null) {
            return;
        }
        if (isMinioStorage(storedFile.storageType())) {
            deleteMinioFileQuietly(storedFile);
        } else {
            deleteLocalFileQuietly(storedFile.filePath());
        }
    }

    private void deleteMinioFileQuietly(StoredTemplateFile storedFile) {
        if (!StringUtils.hasText(storedFile.bucketName()) || !StringUtils.hasText(storedFile.objectName())) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storedFile.bucketName())
                    .object(storedFile.objectName())
                    .build());
        } catch (Exception ignored) {
            // DB deletion is the source of truth for template removal.
        }
    }

    private void deleteLocalFileQuietly(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredPath(filePath));
        } catch (IOException ignored) {
            // DB deletion is the source of truth for template removal.
        }
    }

    private void validateTemplateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("name is too long");
        }
    }

    private String normalizeReportTypeOrNull(String reportType) {
        if (!StringUtils.hasText(reportType)) {
            return null;
        }
        return normalizeReportType(reportType);
    }

    private String normalizeReportType(String reportType) {
        if (!StringUtils.hasText(reportType)) {
            throw new IllegalArgumentException("reportType is required");
        }
        try {
            return ReportType.valueOf(reportType.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported reportType: " + reportType);
        }
    }

    private String normalizeVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return DEFAULT_VERSION;
        }
        String normalized = version.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("version is too long");
        }
        return normalized;
    }

    private void validateConfigJson(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return;
        }
        try {
            objectMapper.readTree(configJson);
        } catch (IOException e) {
            throw new IllegalArgumentException("configJson must be valid JSON");
        }
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private void applyStoredFile(ReportTemplateEntity entity, StoredTemplateFile storedFile) {
        entity.setStorageType(storedFile.storageType());
        entity.setFilePath(storedFile.filePath());
        entity.setBucketName(storedFile.bucketName());
        entity.setObjectName(storedFile.objectName());
        entity.setOriginalFileName(storedFile.originalFileName());
        entity.setContentType(storedFile.contentType());
        entity.setFileSize(storedFile.fileSize());
    }

    private StoredTemplateFile toStoredFile(ReportTemplateEntity entity) {
        String storageType = resolveStorageType(entity);
        MinioObject minioObject = parseMinioPath(entity.getFilePath());
        String bucketName = StringUtils.hasText(entity.getBucketName())
                ? entity.getBucketName()
                : minioObject.bucketName();
        String objectName = StringUtils.hasText(entity.getObjectName())
                ? entity.getObjectName()
                : minioObject.objectName();
        return new StoredTemplateFile(
                storageType,
                entity.getFilePath(),
                bucketName,
                objectName,
                entity.getOriginalFileName(),
                entity.getContentType(),
                entity.getFileSize()
        );
    }

    private String resolveStorageType(ReportTemplateEntity entity) {
        if (StringUtils.hasText(entity.getBucketName()) && StringUtils.hasText(entity.getObjectName())) {
            return "MINIO";
        }
        if (StringUtils.hasText(entity.getFilePath()) && entity.getFilePath().startsWith("minio://")) {
            return "MINIO";
        }
        if (StringUtils.hasText(entity.getStorageType()) && !isMinioStorage(entity.getStorageType())) {
            return entity.getStorageType();
        }
        return "LOCAL";
    }

    private MinioObject parseMinioPath(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.startsWith("minio://")) {
            return new MinioObject(null, null);
        }
        String path = filePath.substring("minio://".length());
        int slashIndex = path.indexOf('/');
        if (slashIndex <= 0 || slashIndex == path.length() - 1) {
            return new MinioObject(null, null);
        }
        return new MinioObject(path.substring(0, slashIndex), path.substring(slashIndex + 1));
    }

    private boolean isMinioStorage(String storageType) {
        return "MINIO".equalsIgnoreCase(storageType);
    }

    private String normalizeContentType(String contentType) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return StoredTemplateFile.DOCX_CONTENT_TYPE;
    }

    private TemplateResponse toResponse(ReportTemplateEntity entity) {
        TemplateResponse response = new TemplateResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setReportType(entity.getReportType());
        response.setVersion(entity.getVersion());
        response.setStorageType(resolveStorageType(entity));
        response.setFilePath(entity.getFilePath());
        response.setBucketName(entity.getBucketName());
        response.setObjectName(entity.getObjectName());
        response.setOriginalFileName(entity.getOriginalFileName());
        response.setContentType(entity.getContentType());
        response.setFileSize(entity.getFileSize());
        response.setConfigJson(entity.getConfigJson());
        response.setEnabled(entity.getEnabled());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private record StoredTemplateFile(
            String storageType,
            String filePath,
            String bucketName,
            String objectName,
            String originalFileName,
            String contentType,
            Long fileSize
    ) {
        private static final String DOCX_CONTENT_TYPE =
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        private String downloadFileName() {
            return StringUtils.hasText(originalFileName) ? originalFileName : "template.docx";
        }

        private String safeContentType() {
            return StringUtils.hasText(contentType) ? contentType : DOCX_CONTENT_TYPE;
        }
    }

    private record MinioObject(String bucketName, String objectName) {
    }
}
