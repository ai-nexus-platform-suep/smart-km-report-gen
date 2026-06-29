package com.km.service;

import com.km.dto.request.SearchRequest;
import com.km.dto.response.SearchResultVO;

public interface SearchService {
    SearchResultVO search(SearchRequest request);
}
