package com.powerreport.admin.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerreport.admin.config.AssetStorageProperties;
import com.powerreport.admin.dto.AssetFileResource;
import com.powerreport.admin.dto.AssetImportResultResponse;
import com.powerreport.admin.dto.AssetPageResponse;
import com.powerreport.admin.dto.AssetResponse;
import com.powerreport.admin.dto.AssetUpdateRequest;
import com.powerreport.admin.service.AssetService;
import com.powerreport.entity.ProjectAssetEntity;
import com.powerreport.enums.AssetCategory;
import com.powerreport.mapper.ProjectAssetMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetServiceImpl implements AssetService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "xlsx", "xls", "docx", "doc", "csv");

    private final ProjectAssetMapper assetMapper;
    private final AssetStorageProperties storageProperties;
    private final MinioClient minioClient;

    public AssetServiceImpl(ProjectAssetMapper assetMapper,
                            AssetStorageProperties storageProperties,
                            @Qualifier("assetMinioClient") MinioClient minioClient) {
        this.assetMapper = assetMapper;
        this.storageProperties = storageProperties;
        this.minioClient = minioClient;
    }

    @Override
    public AssetPageResponse list(Integer page, Integer size, String category, Boolean enabled, String keyword) {
        int actualPage = normalizePage(page);
        int actualSize = normalizeSize(size);
        LambdaQueryWrapper<ProjectAssetEntity> query = new LambdaQueryWrapper<ProjectAssetEntity>()
                .eq(StringUtils.hasText(category), ProjectAssetEntity::getCategory, normalizeCategoryOrNull(category))
                .eq(enabled != null, ProjectAssetEntity::getEnabled, enabled)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(ProjectAssetEntity::getName, keyword)
                        .or()
                        .like(ProjectAssetEntity::getDescription, keyword)
                        .or()
                        .like(ProjectAssetEntity::getTags, keyword))
                .orderByDesc(ProjectAssetEntity::getUpdatedAt)
                .orderByDesc(ProjectAssetEntity::getCreatedAt);

        Page<ProjectAssetEntity> result = assetMapper.selectPage(Page.of(actualPage, actualSize), query);

        AssetPageResponse response = new AssetPageResponse();
        response.setTotal(result.getTotal());
        response.setPage(actualPage);
        response.setSize(actualSize);
        response.setRecords(result.getRecords().stream().map(this::toResponse).toList());
        return response;
    }

    @Override
    public AssetResponse upload(MultipartFile file, String name, String category, String description,
                                  String tags, Boolean enabled, String username) {
        validateAssetName(name);
        String normalizedCategory = normalizeCategory(category);
        StoredAssetFile storedFile = storeFile(file, normalizedCategory);

        ProjectAssetEntity entity = new ProjectAssetEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name.trim());
        entity.setCategory(normalizedCategory);
        entity.setFileType(storedFile.extension());
        applyStoredFile(entity, storedFile);
        entity.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        entity.setTags(StringUtils.hasText(tags) ? tags.trim() : null);
        entity.setEnabled(enabled == null || enabled);
        entity.setCreatedBy(StringUtils.hasText(username) ? username : "local_user");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        assetMapper.insert(entity);
        return toResponse(entity);
    }

    @Override
    public AssetResponse detail(String assetId) {
        return toResponse(requireAsset(assetId));
    }

    @Override
    public AssetResponse update(String assetId, AssetUpdateRequest request) {
        ProjectAssetEntity entity = requireAsset(assetId);
        if (StringUtils.hasText(request.getName())) {
            validateAssetName(request.getName());
            entity.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getCategory())) {
            entity.setCategory(normalizeCategory(request.getCategory()));
        }
        if (request.getDescription() != null) {
            entity.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        }
        if (request.getTags() != null) {
            entity.setTags(StringUtils.hasText(request.getTags()) ? request.getTags().trim() : null);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        assetMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    public AssetFileResource loadFile(String assetId) {
        ProjectAssetEntity entity = requireAsset(assetId);
        StoredAssetFile storedFile = toStoredFile(entity);
        return isMinioStorage(storedFile.storageType())
                ? loadMinioFile(entity, storedFile)
                : loadLocalFile(entity, storedFile);
    }

    @Override
    public void delete(String assetId) {
        ProjectAssetEntity entity = requireAsset(assetId);
        assetMapper.deleteById(assetId);
        deleteStoredFileQuietly(toStoredFile(entity));
    }

    @Override
    public AssetImportResultResponse importSeed(String username) {
        Path seedRoot = resolveSeedRoot();
        if (!Files.exists(seedRoot) || !Files.isDirectory(seedRoot)) {
            throw new IllegalArgumentException("seed directory does not exist: " + seedRoot);
        }

        AssetImportResultResponse result = new AssetImportResultResponse();
        try (Stream<Path> paths = Files.walk(seedRoot, FileVisitOption.FOLLOW_LINKS)) {
            List<Path> candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isImportCandidate)
                    .toList();
            result.setScanned(candidates.size());

            for (Path source : candidates) {
                try {
                    if (importSeedFile(source, username)) {
                        result.setImported(result.getImported() + 1);
                    } else {
                        result.setSkipped(result.getSkipped() + 1);
                    }
                } catch (RuntimeException ex) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(source.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan seed directory");
        }
        return result;
    }

    private boolean importSeedFile(Path source, String username) throws IOException {
        String fileName = source.getFileName().toString();
        String extension = extensionOf(fileName);
        String sha256 = computeSha256(source);

        Long existing = assetMapper.selectCount(new LambdaQueryWrapper<ProjectAssetEntity>()
                .eq(ProjectAssetEntity::getSha256, sha256));
        if (existing != null && existing > 0) {
            return false;
        }

        String displayName = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;
        String category = detectCategory(source).name();
        StoredAssetFile storedFile = storeFile(source, fileName, category, extension, sha256);

        ProjectAssetEntity entity = new ProjectAssetEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(displayName);
        entity.setCategory(category);
        entity.setFileType(storedFile.extension());
        applyStoredFile(entity, storedFile);
        entity.setDescription("从项目素材导入");
        entity.setTags(null);
        entity.setEnabled(true);
        entity.setCreatedBy(StringUtils.hasText(username) ? username : "local_user");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        assetMapper.insert(entity);
        return true;
    }

    private AssetCategory detectCategory(Path source) {
        String normalized = source.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("标准文档") || normalized.contains("知识问答")) {
            return AssetCategory.STANDARD_DOC;
        }
        if (normalized.contains("报告生成")) {
            return AssetCategory.REPORT_DATA;
        }
        return AssetCategory.OTHER;
    }

    private boolean isImportCandidate(Path path) {
        String name = path.getFileName().toString();
        if (name.startsWith("._") || name.startsWith(".")) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/');
        if (normalized.contains("__MACOSX")
                || normalized.contains("/.omc/")
                || normalized.contains("/.claude/")
                || normalized.contains("/.desensitize_backup/")) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(extensionOf(name));
    }

    private ProjectAssetEntity requireAsset(String assetId) {
        if (!StringUtils.hasText(assetId)) {
            throw new IllegalArgumentException("assetId is required");
        }
        ProjectAssetEntity entity = assetMapper.selectById(assetId);
        if (entity == null) {
            throw new IllegalArgumentException("asset does not exist");
        }
        return entity;
    }

    private StoredAssetFile storeFile(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("asset file is required");
        }
        String originalFileName = cleanOriginalFileName(file);
        String extension = extensionOf(originalFileName);
        String contentType = normalizeContentType(file.getContentType(), extension);
        try {
            byte[] bytes = file.getBytes();
            String sha256 = computeSha256(bytes);
            return isMinioStorage(storageProperties.getStorageType())
                    ? storeMinioFile(bytes, originalFileName, category, extension, contentType, sha256)
                    : storeLocalFile(bytes, originalFileName, extension, contentType, sha256);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read asset file");
        }
    }

    private StoredAssetFile storeFile(Path source, String originalFileName, String category, String extension,
                                      String sha256) throws IOException {
        String contentType = contentTypeFor(extension);
        return isMinioStorage(storageProperties.getStorageType())
                ? storeMinioFile(source, originalFileName, category, extension, contentType, sha256)
                : storeLocalFile(source, originalFileName, extension, contentType, sha256);
    }

    private StoredAssetFile storeMinioFile(byte[] bytes, String originalFileName, String category, String extension,
                                           String contentType, String sha256) {
        AssetStorageProperties.Minio minio = storageProperties.getMinio();
        String bucketName = minio.getBucketName();
        String objectName = buildObjectName(minio.getObjectPrefix(), category, extension);
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            ensureBucket(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, bytes.length, -1)
                    .contentType(contentType)
                    .build());
            return new StoredAssetFile(
                    "MINIO",
                    buildMinioPath(bucketName, objectName),
                    bucketName,
                    objectName,
                    originalFileName,
                    contentType,
                    extension,
                    (long) bytes.length,
                    sha256
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to store asset file to MinIO: " + e.getMessage());
        }
    }

    private StoredAssetFile storeMinioFile(Path source, String originalFileName, String category, String extension,
                                           String contentType, String sha256) throws IOException {
        AssetStorageProperties.Minio minio = storageProperties.getMinio();
        String bucketName = minio.getBucketName();
        String objectName = buildObjectName(minio.getObjectPrefix(), category, extension);
        long fileSize = Files.size(source);
        try (InputStream inputStream = Files.newInputStream(source)) {
            ensureBucket(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, fileSize, -1)
                    .contentType(contentType)
                    .build());
            return new StoredAssetFile(
                    "MINIO",
                    buildMinioPath(bucketName, objectName),
                    bucketName,
                    objectName,
                    originalFileName,
                    contentType,
                    extension,
                    fileSize,
                    sha256
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to store asset file to MinIO: " + e.getMessage());
        }
    }

    private StoredAssetFile storeLocalFile(byte[] bytes, String originalFileName, String extension,
                                           String contentType, String sha256) {
        try {
            Path root = storageRoot();
            Files.createDirectories(root);
            String storedFileName = UUID.randomUUID() + "." + extension;
            Path target = root.resolve(storedFileName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("invalid asset storage path");
            }
            Files.write(target, bytes);
            return new StoredAssetFile(
                    "LOCAL",
                    target.toString(),
                    null,
                    null,
                    originalFileName,
                    contentType,
                    extension,
                    Files.size(target),
                    sha256
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to store asset file");
        }
    }

    private StoredAssetFile storeLocalFile(Path source, String originalFileName, String extension,
                                           String contentType, String sha256) throws IOException {
        Path root = storageRoot();
        Files.createDirectories(root);
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("invalid asset storage path");
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredAssetFile(
                "LOCAL",
                target.toString(),
                null,
                null,
                originalFileName,
                contentType,
                extension,
                Files.size(target),
                sha256
        );
    }

    private AssetFileResource loadMinioFile(ProjectAssetEntity entity, StoredAssetFile storedFile) {
        if (!StringUtils.hasText(storedFile.bucketName()) || !StringUtils.hasText(storedFile.objectName())) {
            throw new IllegalArgumentException("asset MinIO object is empty");
        }
        try {
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(storedFile.bucketName())
                    .object(storedFile.objectName())
                    .build());
            Resource resource = new InputStreamResource(inputStream);
            return new AssetFileResource(
                    storedFile.downloadFileName(entity.getName()),
                    storedFile.safeContentType(),
                    resource,
                    Objects.requireNonNullElse(storedFile.fileSize(), -1L)
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to load asset file from MinIO: " + e.getMessage());
        }
    }

    private AssetFileResource loadLocalFile(ProjectAssetEntity entity, StoredAssetFile storedFile) {
        Path path = resolveStoredPath(storedFile.filePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("asset file does not exist");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            return new AssetFileResource(
                    storedFile.downloadFileName(entity.getName()),
                    storedFile.safeContentType(),
                    resource,
                    Files.size(path)
            );
        } catch (IOException e) {
            throw new IllegalStateException("failed to load asset file");
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

    private void applyStoredFile(ProjectAssetEntity entity, StoredAssetFile storedFile) {
        entity.setStorageType(storedFile.storageType());
        entity.setFilePath(storedFile.filePath());
        entity.setBucketName(storedFile.bucketName());
        entity.setObjectName(storedFile.objectName());
        entity.setOriginalFileName(storedFile.originalFileName());
        entity.setContentType(storedFile.contentType());
        entity.setFileSize(storedFile.fileSize());
        entity.setSha256(storedFile.sha256());
    }

    private StoredAssetFile toStoredFile(ProjectAssetEntity entity) {
        String storageType = resolveStorageType(entity);
        MinioObject minioObject = parseMinioPath(entity.getFilePath());
        String bucketName = StringUtils.hasText(entity.getBucketName())
                ? entity.getBucketName()
                : minioObject.bucketName();
        String objectName = StringUtils.hasText(entity.getObjectName())
                ? entity.getObjectName()
                : minioObject.objectName();
        return new StoredAssetFile(
                storageType,
                entity.getFilePath(),
                bucketName,
                objectName,
                entity.getOriginalFileName(),
                entity.getContentType(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getSha256()
        );
    }

    private String resolveStorageType(ProjectAssetEntity entity) {
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

    private void deleteStoredFileQuietly(StoredAssetFile storedFile) {
        if (storedFile == null) {
            return;
        }
        if (isMinioStorage(storedFile.storageType())) {
            deleteMinioFileQuietly(storedFile);
        } else {
            deleteLocalFileQuietly(storedFile.filePath());
        }
    }

    private void deleteMinioFileQuietly(StoredAssetFile storedFile) {
        if (!StringUtils.hasText(storedFile.bucketName()) || !StringUtils.hasText(storedFile.objectName())) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(storedFile.bucketName())
                    .object(storedFile.objectName())
                    .build());
        } catch (Exception ignored) {
            // DB deletion is the source of truth for asset removal.
        }
    }

    private void deleteLocalFileQuietly(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredPath(filePath));
        } catch (IOException ignored) {
            // DB deletion is the source of truth for asset removal.
        }
    }

    private String cleanOriginalFileName(MultipartFile file) {
        String originalFileName = org.springframework.util.StringUtils.cleanPath(
                Objects.toString(file.getOriginalFilename(), "asset.bin")
        );
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("invalid asset file name");
        }
        String extension = extensionOf(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("unsupported asset file type: " + extension);
        }
        return originalFileName;
    }

    private String buildObjectName(String prefix, String category, String extension) {
        String normalizedPrefix = StringUtils.hasText(prefix) ? trimSlashes(prefix.trim()) : "assets";
        String normalizedCategory = StringUtils.hasText(category) ? category.trim() : "OTHER";
        return normalizedPrefix + "/" + normalizedCategory + "/" + UUID.randomUUID() + "." + extension;
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

    private String computeSha256(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available");
        }
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private String computeSha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available");
        }
        try (InputStream input = Files.newInputStream(path);
             DigestInputStream digestStream = new DigestInputStream(input, digest)) {
            digestStream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Path storageRoot() {
        return Paths.get(storageProperties.getStorageDir()).toAbsolutePath().normalize();
    }

    private Path resolveSeedRoot() {
        return Paths.get(storageProperties.getSeedDir()).toAbsolutePath().normalize();
    }

    private Path resolveStoredPath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("asset file path is empty");
        }
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return storageRoot().resolve(filePath).normalize();
    }

    private void validateAssetName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.length() > 300) {
            throw new IllegalArgumentException("name is too long");
        }
    }

    private String normalizeCategoryOrNull(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        return normalizeCategory(category);
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            throw new IllegalArgumentException("category is required");
        }
        try {
            return AssetCategory.valueOf(category.trim()).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported category: " + category);
        }
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType, String fileType) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return contentTypeFor(fileType);
    }

    private String contentTypeFor(String fileType) {
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private boolean isMinioStorage(String storageType) {
        return "MINIO".equalsIgnoreCase(storageType);
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

    private AssetResponse toResponse(ProjectAssetEntity entity) {
        AssetResponse response = new AssetResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setCategory(entity.getCategory());
        response.setCategoryLabel(categoryLabel(entity.getCategory()));
        response.setFileType(entity.getFileType());
        response.setStorageType(resolveStorageType(entity));
        response.setFilePath(entity.getFilePath());
        response.setBucketName(entity.getBucketName());
        response.setObjectName(entity.getObjectName());
        response.setOriginalFileName(entity.getOriginalFileName());
        response.setContentType(entity.getContentType());
        response.setFileSize(entity.getFileSize());
        response.setSha256(entity.getSha256());
        response.setDescription(entity.getDescription());
        response.setTags(entity.getTags());
        response.setEnabled(entity.getEnabled());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private String categoryLabel(String category) {
        if (!StringUtils.hasText(category)) {
            return "";
        }
        try {
            return AssetCategory.valueOf(category).getLabel();
        } catch (IllegalArgumentException ex) {
            return category;
        }
    }

    private record StoredAssetFile(
            String storageType,
            String filePath,
            String bucketName,
            String objectName,
            String originalFileName,
            String contentType,
            String extension,
            Long fileSize,
            String sha256
    ) {
        private String downloadFileName(String fallbackName) {
            if (StringUtils.hasText(originalFileName)) {
                return originalFileName;
            }
            if (StringUtils.hasText(fallbackName) && StringUtils.hasText(extension)) {
                return fallbackName + "." + extension;
            }
            return "asset";
        }

        private String safeContentType() {
            return StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
        }
    }

    private record MinioObject(String bucketName, String objectName) {
    }
}
