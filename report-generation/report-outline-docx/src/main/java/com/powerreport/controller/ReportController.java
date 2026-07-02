package com.powerreport.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineConfirmResponse;
import com.powerreport.dto.OutlineDraftRequest;
import com.powerreport.dto.OutlineDraftResponse;
import com.powerreport.dto.OutlineGenerateRequest;
import com.powerreport.dto.OutlineGenerateResponse;
import com.powerreport.dto.OutlineNodeResponse;
import com.powerreport.dto.OutlineRequest;
import com.powerreport.dto.ReportDocxExportRequest;
import com.powerreport.dto.ReportFileResponse;
import com.powerreport.dto.StoredReportFile;
import com.powerreport.service.DocxExportService;
import com.powerreport.service.OutlineService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {

    private final OutlineService outlineService;
    private final DocxExportService docxExportService;

    /**
     * Compatibility endpoint: return local fixed outline template.
     */
    @PostMapping("/outline")
    public ApiResponse<List<OutlineNodeResponse>> outline(@Valid @RequestBody OutlineRequest request) {
        return ApiResponse.success(outlineService.buildOutline(request.getReportType()));
    }

    /**
     * Backend 1 scope: call AI outline API, parse and cache outline in Redis.
     */
    @PostMapping("/outline/generate")
    public ApiResponse<OutlineGenerateResponse> generateOutline(
            @Valid @RequestBody OutlineGenerateRequest request
    ) {
        return ApiResponse.success(outlineService.generateOutline(request));
    }

    /**
     * Save frontend-edited outline as a draft report.
     */
    @PostMapping("/outline/draft")
    public ApiResponse<OutlineDraftResponse> createDraftOutline(
            @Valid @RequestBody OutlineDraftRequest request
    ) {
        return ApiResponse.success(outlineService.createDraftOutline(request));
    }

    /**
     * Update an existing draft outline.
     */
    @PutMapping("/{reportId}/outline/draft")
    public ApiResponse<OutlineDraftResponse> updateDraftOutline(
            @PathVariable String reportId,
            @Valid @RequestBody OutlineDraftRequest request
    ) {
        return ApiResponse.success(outlineService.updateDraftOutline(reportId, request));
    }

    /**
     * Read saved report metadata and outline tree.
     */
    @GetMapping("/{reportId}/outline")
    public ApiResponse<OutlineDraftResponse> getSavedOutline(@PathVariable String reportId) {
        return ApiResponse.success(outlineService.getSavedOutline(reportId));
    }

    /**
     * Backend 1 scope: save final confirmed outline into reports + report_outline_nodes.
     */
    @PostMapping("/outline/confirm")
    public ApiResponse<OutlineConfirmResponse> confirmOutline(
            @Valid @RequestBody OutlineConfirmRequest request
    ) {
        return ApiResponse.success(outlineService.confirmOutline(request));
    }

    /**
     * Backend 1 scope: rebuild DOCX from saved DB draft without calling LLM.
     */
    @PostMapping("/{reportId}/export/docx")
    public ApiResponse<ReportFileResponse> exportReportDocx(
            @PathVariable String reportId,
            @RequestBody(required = false) ReportDocxExportRequest request
    ) throws IOException {
        ReportDocxExportRequest actualRequest = request == null ? new ReportDocxExportRequest() : request;
        return ApiResponse.success(docxExportService.exportReport(reportId, actualRequest));
    }

    /**
     * Backend 1 scope: download generated DOCX by report_files.id.
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable String fileId) throws IOException {
        StoredReportFile file = docxExportService.getFileForDownload(fileId);
        byte[] bytes = Files.readAllBytes(file.path());
        String encodedName = encodeDownloadName(file.fileName());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private String encodeDownloadName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
