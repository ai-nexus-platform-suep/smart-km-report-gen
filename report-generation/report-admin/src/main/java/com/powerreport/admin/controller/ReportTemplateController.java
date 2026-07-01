package com.powerreport.admin.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.admin.dto.TemplateConfigRequest;
import com.powerreport.admin.dto.TemplateConfigSchemaResponse;
import com.powerreport.admin.dto.TemplateFileResource;
import com.powerreport.admin.dto.TemplatePageResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateUpdateRequest;
import com.powerreport.admin.dto.TemplateVisualConfigDto;
import com.powerreport.admin.service.TemplateService;
import com.powerreport.admin.service.TemplateVisualEditorService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/templates")
public class ReportTemplateController {

    private final TemplateService templateService;
    private final TemplateVisualEditorService visualEditorService;

    @GetMapping("/config-schema")
    public ApiResponse<TemplateConfigSchemaResponse> configSchema() {
        return ApiResponse.success(visualEditorService.getConfigSchema());
    }

    @GetMapping("/defaults/{reportType}")
    public ApiResponse<TemplateVisualConfigDto> defaultConfig(@PathVariable String reportType) {
        return ApiResponse.success(visualEditorService.getDefaultConfig(reportType));
    }

    @GetMapping
    public ApiResponse<TemplatePageResponse> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(templateService.list(page, size, reportType, enabled, keyword));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TemplateResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String name,
            @RequestParam String reportType,
            @RequestParam(defaultValue = "1.0.0") String version,
            @RequestParam(required = false) String configJson,
            @RequestParam(defaultValue = "true") Boolean enabled,
            @RequestHeader(value = "X-Username", defaultValue = "local_user") String username
    ) {
        return ApiResponse.success(templateService.upload(
                file,
                name,
                reportType,
                version,
                configJson,
                enabled,
                username
        ));
    }

    @GetMapping("/{templateId}")
    public ApiResponse<TemplateResponse> detail(@PathVariable String templateId) {
        return ApiResponse.success(templateService.detail(templateId));
    }

    @PutMapping("/{templateId}")
    public ApiResponse<TemplateResponse> update(
            @PathVariable String templateId,
            @Valid @RequestBody TemplateUpdateRequest request
    ) {
        return ApiResponse.success(templateService.update(templateId, request));
    }

    @PutMapping(value = "/{templateId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TemplateResponse> replaceFile(
            @PathVariable String templateId,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(templateService.replaceFile(templateId, file));
    }

    @GetMapping("/{templateId}/config")
    public ApiResponse<String> getConfig(@PathVariable String templateId) {
        return ApiResponse.success(templateService.getConfig(templateId));
    }

    @PutMapping("/{templateId}/config")
    public ApiResponse<TemplateResponse> updateConfig(
            @PathVariable String templateId,
            @Valid @RequestBody TemplateConfigRequest request
    ) {
        return ApiResponse.success(templateService.updateConfig(templateId, request));
    }

    @GetMapping("/{templateId}/visual-config")
    public ApiResponse<TemplateVisualConfigDto> getVisualConfig(@PathVariable String templateId) {
        return ApiResponse.success(visualEditorService.getVisualConfig(templateId));
    }

    @PutMapping("/{templateId}/visual-config")
    public ApiResponse<TemplateResponse> updateVisualConfig(
            @PathVariable String templateId,
            @Valid @RequestBody TemplateVisualConfigDto config
    ) {
        return ApiResponse.success(visualEditorService.updateVisualConfig(templateId, config));
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(@PathVariable String templateId) {
        templateService.delete(templateId);
        return ApiResponse.success();
    }

    @GetMapping("/{templateId}/download")
    public ResponseEntity<Resource> download(@PathVariable String templateId) {
        TemplateFileResource file = templateService.loadFile(templateId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(file.resource());
    }
}
