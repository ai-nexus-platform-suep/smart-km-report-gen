package com.powerreport.controller;

import com.powerreport.common.ApiResult;
import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineConfirmResponse;
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
    public ApiResult<List<OutlineNodeResponse>> outline(@Valid @RequestBody OutlineRequest request) {
        return ApiResult.ok(outlineService.buildOutline(request.getReportType()));
    }

    /**
     * Backend 1 scope: call AI outline API, parse and cache outline in Redis.
     */
    @PostMapping("/outline/generate")
    public ApiResult<OutlineGenerateResponse> generateOutline(
            @Valid @RequestBody OutlineGenerateRequest request
    ) {
        return ApiResult.ok(outlineService.generateOutline(request));
    }

    /**
     * Backend 1 scope: save final confirmed outline into reports + report_outline_nodes.
     */
    @PostMapping("/outline/confirm")
    public ApiResult<OutlineConfirmResponse> confirmOutline(
            @Valid @RequestBody OutlineConfirmRequest request
    ) {
        return ApiResult.ok(outlineService.confirmOutline(request));
    }

    /**
     * Backend 1 scope: rebuild DOCX from saved DB draft without calling LLM.
     */
    @PostMapping("/{reportId}/export/docx")
    public ApiResult<ReportFileResponse> exportReportDocx(
            @PathVariable String reportId,
            @RequestBody(required = false) ReportDocxExportRequest request
    ) throws IOException {
        ReportDocxExportRequest actualRequest = request == null ? new ReportDocxExportRequest() : request;
        return ApiResult.ok(docxExportService.exportReport(reportId, actualRequest));
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
