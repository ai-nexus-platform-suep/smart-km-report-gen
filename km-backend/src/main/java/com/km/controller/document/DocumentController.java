package com.km.controller.document;

import com.km.common.dto.ApiResponse;
import com.km.common.dto.PageResult;
import com.km.controller.support.RequestUserResolver;
import com.km.dto.request.BatchDeleteRequest;
import com.km.dto.request.UpdateDocumentTagsRequest;
import com.km.dto.response.DocumentBatchDeleteResponse;
import com.km.dto.response.DocumentDeleteResponse;
import com.km.dto.response.DocumentUploadResponse;
import com.km.service.DocumentService;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * 文档管理控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final RequestUserResolver requestUserResolver;

    /**
     * 上传文档到知识库
     * POST /api/knowledge-bases/{kbId}/documents
     */
    @Operation(summary = "上传文档到知识库", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = DocumentUploadForm.class))))
    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentUploadResponse> uploadDocument(
            @PathVariable String kbId,
            @Parameter(hidden = true) @RequestPart("file") MultipartFile file,
            @Parameter(hidden = true) @RequestPart(value = "tags", required = false) String tags,
            @Parameter(name = "userid", description = "用户 ID", required = true, in = ParameterIn.HEADER)
            @RequestHeader(value = "userid", required = false) String userIdHeader) {
        Long userId = requestUserResolver.requireUserId(userIdHeader);
        DocumentUploadResponse result = documentService.uploadDocument(kbId, file, tags, userId);
        return ApiResponse.ok(result);
    }

    /**
     * 查询知识库内的文档列表（分页）
     * GET /api/knowledge-bases/{kbId}/documents
     */
    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<PageResult<DocumentVO>> listDocuments(
            @PathVariable String kbId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<DocumentVO> result = documentService.listDocuments(kbId, status, page, pageSize);
        return ApiResponse.ok(result);
    }

    /**
     * 删除知识库内的单个文档
     * DELETE /api/knowledge-bases/{kbId}/documents/{docId}
     */
    @DeleteMapping("/knowledge-bases/{kbId}/documents/{docId}")
    public ApiResponse<DocumentDeleteResponse> deleteDocument(
            @PathVariable String kbId,
            @PathVariable String docId) {
        DocumentDeleteResponse result = documentService.deleteDocument(kbId, docId);
        return ApiResponse.ok(result);
    }

    /**
     * 批量删除知识库内的文档
     * POST /api/knowledge-bases/{kbId}/documents/batch-delete
     */
    @PostMapping("/knowledge-bases/{kbId}/documents/batch-delete")
    public ApiResponse<DocumentBatchDeleteResponse> batchDeleteDocuments(
            @PathVariable String kbId,
            @Valid @RequestBody BatchDeleteRequest request) {
        DocumentBatchDeleteResponse result = documentService.batchDeleteDocuments(kbId, request.getIds());
        return ApiResponse.ok(result);
    }

    /**
     * 获取文档元数据
     * GET /api/documents/{id}
     */
    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentVO> getDocument(@PathVariable("id") String docId) {
        DocumentVO result = documentService.getDocument(docId);
        return ApiResponse.ok(result);
    }

    /**
     * 获取文档切片列表（分页）
     * GET /api/documents/{id}/chunks
     */
    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<PageResult<ChunkVO>> listChunks(
            @PathVariable("id") String docId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        PageResult<ChunkVO> result = documentService.listChunks(docId, page, pageSize);
        return ApiResponse.ok(result);
    }

    /**
     * 下载原始文件
     * GET /api/documents/{id}/download
     */
    @GetMapping("/documents/{id}/download")
    public void downloadDocument(@PathVariable("id") String docId, HttpServletResponse response) {
        documentService.downloadDocument(docId, response);
    }

    /**
     * 重试失败的文档处理
     * POST /api/documents/{id}/retry
     */
    @PostMapping("/documents/{id}/retry")
    public ApiResponse<DocumentVO> retryProcess(@PathVariable("id") String docId) {
        DocumentVO result = documentService.retryProcess(docId);
        return ApiResponse.ok(result);
    }

    /**
     * 更新文档标签
     * PUT /api/documents/{id}/tags
     */
    @PutMapping("/documents/{id}/tags")
    public ApiResponse<DocumentVO> updateTags(
            @PathVariable("id") String docId,
            @Valid @RequestBody UpdateDocumentTagsRequest request) {
        DocumentVO result = documentService.updateTags(docId, request.getTags());
        return ApiResponse.ok(result);
    }

    public static class DocumentUploadForm {
        @Schema(description = "上传文件", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
        public MultipartFile file;

        @Schema(description = "标签 JSON 字符串", type = "string", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        public String tags;
    }
}
