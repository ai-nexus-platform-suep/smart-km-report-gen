package com.km.controller.search;

import com.km.common.dto.ApiResponse;
import com.km.controller.support.RequestUserResolver;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
import org.springframework.web.bind.annotation.*;

/**
 * 对外检索 API 控制器。
 * 契约见 docs/api-contract.yaml，PRD 6.5 前台知识检索 & 6.9 对外 API。
 * 路径：POST /api/search，供问答组（feat-b）和报告组（feat-c）消费。
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;
    private final RequestUserResolver requestUserResolver;

    public SearchController(SearchService searchService, RequestUserResolver requestUserResolver) {
        this.searchService = searchService;
        this.requestUserResolver = requestUserResolver;
    }

    /**
     * 执行知识检索。支持 vector / vector_rerank 两种模式。
     * MVP 阶段 JWT 暂未启用，userId 从 userid 头取。
     */
    @PostMapping("/search")
    public ApiResponse<SearchResultVO> search(@RequestBody SearchRequest request,
                                               @RequestHeader(value = "userid", required = false) String userIdHeader) {
        Long userId = requestUserResolver.requireUserId(userIdHeader);
        SearchResultVO result = searchService.search(request, userId);
        return ApiResponse.ok(result);
    }
}
