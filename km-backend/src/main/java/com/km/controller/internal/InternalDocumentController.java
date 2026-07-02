package com.km.controller.internal;

import com.km.common.dto.ApiResponse;
import com.km.dto.request.DocumentStatusUpdateRequest;
import com.km.dto.request.ReplaceDocumentChunksRequest;
import com.km.dto.response.ReplaceDocumentChunksResponse;
import com.km.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/internal/documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final DocumentService documentService;

    @PostMapping("/status")
    public ApiResponse<Void> updateStatus(@Valid @RequestBody DocumentStatusUpdateRequest request) {
        documentService.updateStatus(request.getDocumentId(), request.getStatus(), request.getErrorMsg());
        log.info("Document status updated by worker, docId={}, status={}, jobId={}, attempt={}, chunkCount={}",
                request.getDocumentId(), request.getStatus(), request.getJobId(), request.getAttempt(), request.getChunkCount());
        return ApiResponse.ok();
    }

    @PostMapping("/{documentId}/chunks:replace")
    public ApiResponse<ReplaceDocumentChunksResponse> replaceChunks(@PathVariable String documentId,
                                                                    @Valid @RequestBody ReplaceDocumentChunksRequest request) {
        ReplaceDocumentChunksResponse response = documentService.replaceChunks(documentId, request);
        log.info("Document chunks replaced by worker, docId={}, jobId={}, chunkCount={}",
                documentId, request.getJobId(), response.getChunkCount());
        return ApiResponse.ok(response);
    }
}
