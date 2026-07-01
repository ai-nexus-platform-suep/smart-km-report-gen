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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
        String filePath = storeFile(file);

        ReportTemplateEntity entity = new ReportTemplateEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name.trim());
        entity.setReportType(normalizedType);
        entity.setVersion(normalizedVersion);
        entity.setFilePath(filePath);
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
        String oldPath = entity.getFilePath();
        entity.setFilePath(storeFile(file));
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(entity);
        deleteFileQuietly(oldPath);
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
        Path path = resolveStoredPath(entity.getFilePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("template file does not exist");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            String fileName = path.getFileName().toString();
            return new TemplateFileResource(fileName, resource, Files.size(path));
        } catch (IOException e) {
            throw new IllegalStateException("failed to load template file");
        }
    }

    @Override
    public void delete(String templateId) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        templateMapper.deleteById(templateId);
        deleteFileQuietly(entity.getFilePath());
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

    private String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("template file is required");
        }
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
        try {
            Path root = storageRoot();
            Files.createDirectories(root);
            String storedFileName = UUID.randomUUID() + ".docx";
            Path target = root.resolve(storedFileName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("invalid template storage path");
            }
            file.transferTo(target);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalStateException("failed to store template file");
        }
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

    private void deleteFileQuietly(String filePath) {
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

    private TemplateResponse toResponse(ReportTemplateEntity entity) {
        TemplateResponse response = new TemplateResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setReportType(entity.getReportType());
        response.setVersion(entity.getVersion());
        response.setFilePath(entity.getFilePath());
        response.setConfigJson(entity.getConfigJson());
        response.setEnabled(entity.getEnabled());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
