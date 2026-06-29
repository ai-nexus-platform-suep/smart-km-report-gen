package com.km.service;

import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.common.dto.PageResult;
import java.util.List;

public interface KnowledgeBaseService {
    KnowledgeBaseVO create(CreateKnowledgeBaseRequest request, Long ownerId);
    KnowledgeBaseVO update(String id, UpdateKnowledgeBaseRequest request);
    void delete(String id);
    void batchDelete(List<String> ids);
    KnowledgeBaseVO getById(String id);
    PageResult<KnowledgeBaseVO> list(String keyword, String docType, int page, int pageSize);
}
