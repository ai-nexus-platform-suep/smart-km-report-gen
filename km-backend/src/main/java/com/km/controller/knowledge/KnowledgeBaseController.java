package com.km.controller.knowledge;

import com.km.common.dto.ApiResponse;
import com.km.common.dto.PageResult;
import com.km.dto.request.BatchDeleteRequest;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public ApiResponse<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(knowledgeBaseService.list(docType, keyword, page, pageSize));
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        // TODO: 接入认证后从 @RequestAttribute 获取 userId
        return ApiResponse.ok(knowledgeBaseService.create(request, 0L));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> get(@PathVariable String id) {
        return ApiResponse.ok(knowledgeBaseService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeBaseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        knowledgeBaseService.deleteById(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/batch")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchDeleteRequest request) {
        knowledgeBaseService.batchDelete(request.getIds());
        return ApiResponse.ok();
    }
}
