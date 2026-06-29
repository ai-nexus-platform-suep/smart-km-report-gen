package com.km.controller.search;

import com.km.common.dto.ApiResponse;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 对外检索 API 控制器。
 * 契约见 docs/api-contract.yaml，PRD 6.5 前台知识检索 & 6.9 对外 API。
 * 路径：POST /api/search，供问答组（feat-b）和报告组（feat-c）消费。
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 执行知识检索。支持 vector / vector_rerank 两种模式。
     * MVP 阶段 JWT 暂未启用，userId 默认 0 或从 X-User-Id 头取。
     */
    @PostMapping("/search")
    public ApiResponse<SearchResultVO> search(@RequestBody SearchRequest request,
                                              HttpServletRequest httpRequest) {
        Long userId = 0L;
        String userIdHeader = httpRequest.getHeader("X-User-Id");
        if (userIdHeader != null) {
            try { userId = Long.parseLong(userIdHeader); } catch (NumberFormatException ignored) {}
        }
        SearchResultVO result = searchService.search(request, userId);
        return ApiResponse.ok(result);
    }
}
