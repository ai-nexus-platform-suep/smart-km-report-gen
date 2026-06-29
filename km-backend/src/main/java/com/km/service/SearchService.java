package com.km.service;

import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;

/**
 * 前台知识检索服务。
 * 编排向量检索 + 重排序流程，供 SearchController 调用。
 * 对应 EPIC-05 / PRD 6.5 前台知识检索 & 6.9 对外 API。
 */
public interface SearchService {

    /**
     * 执行知识检索。
     *
     * @param request 检索请求（query、知识库范围、模式、阈值、标签过滤等）
     * @param userId  当前用户 ID（用于鉴权与日志）
     * @return 检索结果
     */
    SearchResultVO search(SearchRequest request, Long userId);
}
