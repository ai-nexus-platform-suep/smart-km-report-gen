package com.km.controller.document;

import com.km.common.dto.ApiResponse;
import com.km.common.dto.PageResult;
import com.km.dto.request.BatchDeleteRequest;
import com.km.dto.request.DocumentTagsRequest;
import com.km.dto.response.*;
import com.km.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<DocumentUploadResultVO> upload(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String tags) {
        // TODO: get userId from JWT token
        return ApiResponse.ok(documentService.upload(kbId, file, tags, 1L));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<PageResult<DocumentVO>> list(
            @PathVariable String kbId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(documentService.listByKbId(kbId, status, page, pageSize));
    }

    @DeleteMapping("/knowledge-bases/{kbId}/documents/{docId}")
    public ApiResponse<DocumentDeleteResultVO> delete(
            @PathVariable String kbId, @PathVariable String docId) {
        return ApiResponse.ok(documentService.delete(kbId, docId));
    }

    @PostMapping("/knowledge-bases/{kbId}/documents/batch-delete")
    public ApiResponse<DocumentBatchDeleteResultVO> batchDelete(
            @PathVariable String kbId, @RequestBody BatchDeleteRequest request) {
        return ApiResponse.ok(documentService.batchDelete(kbId, request.getIds()));
    }

    @PostMapping("/documents/{id}/retry")
    public ApiResponse<DocumentVO> retry(@PathVariable String id) {
        return ApiResponse.ok(documentService.retryProcess(id));
    }

    @PutMapping("/documents/{id}/tags")
    public ApiResponse<DocumentVO> updateTags(@PathVariable String id,
                                               @RequestBody DocumentTagsRequest request) {
        return ApiResponse.ok(documentService.updateTags(id, request.getTags()));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentVO> get(@PathVariable String id) {
        return ApiResponse.ok(documentService.getById(id));
    }

    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<PageResult<?>> listChunks(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.ok(documentService.listChunks(id, page, pageSize));
    }
}
