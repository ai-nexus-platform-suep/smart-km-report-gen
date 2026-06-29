package com.km.service;

import com.km.common.dto.PageResult;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;

public interface KnowledgeBaseService {
    PageResult<KnowledgeBaseVO> list(String docType, String keyword, int page, int pageSize);
    KnowledgeBaseVO create(CreateKnowledgeBaseRequest request, Long ownerId);
    KnowledgeBaseVO getById(String id);
    KnowledgeBaseVO update(String id, UpdateKnowledgeBaseRequest request);
    void deleteById(String id);
    void batchDelete(java.util.List<String> ids);
}
