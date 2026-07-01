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
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "xlsx", "xls", "docx", "doc", "csv");

    private final ProjectAssetMapper assetMapper;
    private final AssetStorageProperties storageProperties;

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
        StoredFile storedFile = storeFile(file);

        ProjectAssetEntity entity = new ProjectAssetEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name.trim());
        entity.setCategory(normalizedCategory);
        entity.setFileType(storedFile.extension());
        entity.setFilePath(storedFile.path());
        entity.setFileSize(storedFile.size());
        entity.setSha256(storedFile.sha256());
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
        Path path = resolveStoredPath(entity.getFilePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("asset file does not exist");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            String fileName = entity.getName() + "." + entity.getFileType();
            return new AssetFileResource(fileName, contentTypeFor(entity.getFileType()), resource, Files.size(path));
        } catch (IOException e) {
            throw new IllegalStateException("failed to load asset file");
        }
    }

    @Override
    public void delete(String assetId) {
        ProjectAssetEntity entity = requireAsset(assetId);
        assetMapper.deleteById(assetId);
        deleteFileQuietly(entity.getFilePath());
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

        Path root = storageRoot();
        Files.createDirectories(root);
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("invalid asset storage path");
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        String displayName = fileName.contains(".")
                ? fileName.substring(0, fileName.lastIndexOf('.'))
                : fileName;

        ProjectAssetEntity entity = new ProjectAssetEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(displayName);
        entity.setCategory(detectCategory(source).name());
        entity.setFileType(extension);
        entity.setFilePath(target.toString());
        entity.setFileSize(Files.size(target));
        entity.setSha256(sha256);
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

    private StoredFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("asset file is required");
        }
        String originalFileName = StringUtils.cleanPath(Objects.toString(file.getOriginalFilename(), "asset.bin"));
        if (originalFileName.contains("..")) {
            throw new IllegalArgumentException("invalid asset file name");
        }
        String extension = extensionOf(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("unsupported asset file type: " + extension);
        }
        try {
            Path root = storageRoot();
            Files.createDirectories(root);
            String storedFileName = UUID.randomUUID() + "." + extension;
            Path target = root.resolve(storedFileName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("invalid asset storage path");
            }
            file.transferTo(target);
            return new StoredFile(target.toString(), extension, Files.size(target), computeSha256(target));
        } catch (IOException e) {
            throw new IllegalStateException("failed to store asset file");
        }
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

    private void deleteFileQuietly(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolveStoredPath(filePath));
        } catch (IOException ignored) {
            // DB deletion is the source of truth.
        }
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
        response.setFilePath(entity.getFilePath());
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

    private record StoredFile(String path, String extension, long size, String sha256) {
    }
}
