package com.powerreport.content.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.enums.ContentGenerationMode;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.SectionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/{reportId}/sections")
public class SectionController {

    private final SectionService sectionService;

    /**
     * Start section generation and create missing report_sections rows.
     */
    @PostMapping("/generate")
    public ApiResponse<SectionGenerateResponse> generateSections(
            @PathVariable String reportId,
            @RequestBody(required = false) SectionGenerateRequest request
    ) {
        SectionGenerateRequest actualRequest = request == null ? new SectionGenerateRequest() : request;
        return ApiResponse.success(sectionService.startGeneration(reportId, actualRequest));
    }

    /**
     * Generate editable content from confirmed outline and table plans without calling AI.
     */
    @PostMapping("/generate/template")
    public ApiResponse<SectionGenerateResponse> generateSectionsFromTemplate(@PathVariable String reportId) {
        SectionGenerateRequest request = new SectionGenerateRequest();
        request.setGenerationMode(ContentGenerationMode.TEMPLATE);
        return ApiResponse.success(sectionService.startGeneration(reportId, request));
    }

    /**
     * SSE endpoint for frontend streaming workspace.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSections(@PathVariable String reportId) {
        return sectionService.streamSections(reportId);
    }

    @PutMapping("/{sectionId}")
    public ApiResponse<SectionResponse> saveSection(
            @PathVariable String reportId,
            @PathVariable String sectionId,
            @Valid @RequestBody SectionContentRequest request
    ) {
        return ApiResponse.success(sectionService.saveSection(reportId, sectionId, request));
    }

    @GetMapping("/{sectionId}")
    public ApiResponse<SectionResponse> getSection(
            @PathVariable String reportId,
            @PathVariable String sectionId
    ) {
        return ApiResponse.success(sectionService.getSection(reportId, sectionId));
    }

    @GetMapping
    public ApiResponse<List<SectionResponse>> listSections(@PathVariable String reportId) {
        return ApiResponse.success(sectionService.listSections(reportId));
    }

    /**
     * Bonus endpoint: regenerate one section with a user hint.
     */
    @PostMapping("/{sectionId}/regenerate")
    public ApiResponse<SectionGenerateResponse> regenerateSection(
            @PathVariable String reportId,
            @PathVariable String sectionId,
            @RequestBody(required = false) SectionRegenerateRequest request
    ) {
        SectionRegenerateRequest actualRequest = request == null ? new SectionRegenerateRequest() : request;
        return ApiResponse.success(sectionService.regenerateSection(reportId, sectionId, actualRequest));
    }
}

