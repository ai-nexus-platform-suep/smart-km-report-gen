package com.km.controller.search;

import com.km.common.dto.ApiResponse;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 对外检索 API，契约见 docs/api-contract.yaml。
 * EPIC-06 接入真实向量检索；脚手架阶段返回空结果。
 */
@RestController
@RequestMapping("/api/v1")
public class SearchController {

    @PostMapping("/search")
    public ApiResponse<SearchResultVO> search(@RequestBody SearchRequest request) {
        SearchResultVO result = new SearchResultVO();
        result.setResults(Collections.emptyList());
        result.setTotal(0);
        return ApiResponse.ok(result);
    }
}
