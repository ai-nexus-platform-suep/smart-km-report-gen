package com.powerreport.admin.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.admin.dto.TemplateConfigRequest;
import com.powerreport.admin.dto.TemplateFileResource;
import com.powerreport.admin.dto.TemplatePageResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateUpdateRequest;
import com.powerreport.admin.service.TemplateService;
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

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> delete(@PathVariable String templateId) {
        templateService.delete(templateId);
        return ApiResponse.success();
    }

    @GetMapping("/{templateId}/download")
    public ResponseEntity<Resource> download(@PathVariable String templateId) {
        TemplateFileResource file = templateService.loadFile(templateId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        if (file.contentLength() >= 0) {
            response.contentLength(file.contentLength());
        }
        return response.body(file.resource());
    }
}
