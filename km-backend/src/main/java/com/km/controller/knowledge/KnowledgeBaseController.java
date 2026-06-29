package com.km.controller.knowledge;

import com.km.common.dto.ApiResponse;
import com.km.common.dto.PageResult;
import com.km.dto.request.BatchDeleteRequest;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    public KnowledgeBaseController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    @GetMapping
    public ApiResponse<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(kbService.list(keyword, docType, page, pageSize));
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(@RequestBody CreateKnowledgeBaseRequest request) {
        // TODO: get ownerId from JWT token
        return ApiResponse.ok(kbService.create(request, 1L));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> get(@PathVariable String id) {
        return ApiResponse.ok(kbService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable String id,
                                                @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.ok(kbService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        kbService.delete(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/batch")
    public ApiResponse<Void> batchDelete(@RequestBody BatchDeleteRequest request) {
        kbService.batchDelete(request.getIds());
        return ApiResponse.ok();
    }
}
