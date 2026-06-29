package com.km.controller.search;

import com.km.common.dto.ApiResponse;
import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;
import com.km.service.SearchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public ApiResponse<SearchResultVO> search(@RequestBody SearchRequest request) {
        return ApiResponse.ok(searchService.search(request));
    }
}
