package com.km.service.impl;

import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.entity.KnowledgeBase;
import com.km.repository.KnowledgeBaseMapper;
import com.km.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public PageResult<KnowledgeBaseVO> list(String docType, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<KnowledgeBase> list = knowledgeBaseMapper.findAll(docType, keyword, offset, pageSize);
        int total = knowledgeBaseMapper.countAll(docType, keyword);
        List<KnowledgeBaseVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, total, page, pageSize);
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest request, Long ownerId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(UUID.randomUUID().toString().replace("-", ""));
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setDocType(request.getDocType() != null ? request.getDocType() : "通用文档");
        kb.setChunkStrategyJson(request.getChunkStrategy());
        kb.setSearchStrategy(request.getSearchStrategy() != null ? request.getSearchStrategy() : "vector_rerank");
        kb.setOwnerId(ownerId);
        knowledgeBaseMapper.insert(kb);
        return toVO(kb);
    }

    @Override
    public KnowledgeBaseVO getById(String id) {
        KnowledgeBase kb = knowledgeBaseMapper.getById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        return toVO(kb);
    }

    @Override
    @Transactional
    public KnowledgeBaseVO update(String id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase existing = knowledgeBaseMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setChunkStrategyJson(request.getChunkStrategy());
        kb.setSearchStrategy(request.getSearchStrategy());
        knowledgeBaseMapper.updateById(kb);
        return getById(id);
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        KnowledgeBase existing = knowledgeBaseMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        knowledgeBaseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        knowledgeBaseMapper.batchDelete(ids);
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setDocType(kb.getDocType());
        vo.setDocCount(kb.getDocCount());
        vo.setChunkStrategy(kb.getChunkStrategyJson());
        vo.setSearchStrategy(kb.getSearchStrategy());
        vo.setOwnerId(kb.getOwnerId());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());
        return vo;
    }
}
