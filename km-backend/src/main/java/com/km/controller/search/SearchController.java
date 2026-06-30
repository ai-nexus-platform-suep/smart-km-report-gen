package com.km.controller.search;

import com.km.common.dto.ApiResponse;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * 对外检索 API 控制器。
 * 
 * 对应 EPIC-05
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class SearchController {

    private static final Pattern SEARCH_MODE_PATTERN = 
            Pattern.compile("^(vector|vector_rerank|bm25)$");

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ApiResponse<SearchResultVO> search(@RequestBody SearchRequest request,
                                              HttpServletRequest httpRequest) {
        // ========== 手动参数校验 - 对应 EPIC-05 05.6 ==========
        validateRequest(request);
        
        Long userId = 0L;
        String userIdHeader = httpRequest.getHeader("X-User-Id");
        if (userIdHeader != null) {
            try { 
                userId = Long.parseLong(userIdHeader); 
            } catch (NumberFormatException ignored) {}
        }
        
        long startTime = System.currentTimeMillis();
        log.info("[EPIC-05] Search request start: userId={}, query={}, kbIds={}, mode={}, topK={}, tagFilters={}",
                userId, request.getQuery(), request.getKnowledgeBaseIds(), 
                request.getSearchMode(), request.getTopK(), request.getTagFilters());
        
        SearchResultVO result = searchService.search(request, userId);
        
        long costMs = System.currentTimeMillis() - startTime;
        log.info("[EPIC-05] Search request completed: userId={}, query={}, total={}, cost={}ms",
                userId, request.getQuery(), result.getTotal(), costMs);
        
        return ApiResponse.ok(result);
    }

    /**
     * 手动参数校验
     * 对应 EPIC-05 05.6 参数校验增强
     */
    private void validateRequest(SearchRequest request) {
        // query 校验
        String query = request.getQuery();
        if (query == null || query.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索关键词不能为空");
        }
        if (query.length() > 500) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索关键词长度不能超过500");
        }
        
        // topK 校验
        Integer topK = request.getTopK();
        if (topK != null) {
            if (topK < 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "topK 最小值为 1");
            }
            if (topK > 100) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "topK 最大值为 100");
            }
        }
        
        // searchMode 校验
        String searchMode = request.getSearchMode();
        if (searchMode != null && !SEARCH_MODE_PATTERN.matcher(searchMode).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, 
                    "searchMode 只能是 vector、vector_rerank 或 bm25");
        }
        
        // similarityThreshold 校验
        Float st = request.getSimilarityThreshold();
        if (st != null && (st < 0.0f || st > 1.0f)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, 
                    "similarityThreshold 取值范围为 0.0 ~ 1.0");
        }
        
        // rerankThreshold 校验
        Float rt = request.getRerankThreshold();
        if (rt != null && (rt < 0.0f || rt > 1.0f)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, 
                    "rerankThreshold 取值范围为 0.0 ~ 1.0");
        }
    }
}