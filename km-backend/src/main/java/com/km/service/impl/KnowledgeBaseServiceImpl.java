package com.km.service.impl;

import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.common.util.JsonUtils;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.entity.KnowledgeBase;
import com.km.repository.KnowledgeBaseMapper;
import com.km.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper kbMapper) {
        this.kbMapper = kbMapper;
    }

    @Override
    @Transactional
    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest request, Long ownerId) {
        KnowledgeBase entity = new KnowledgeBase();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setDocType(request.getDocType());
        entity.setChunkStrategyJson(JsonUtils.toJson(request.getChunkStrategy()));
        entity.setSearchStrategy(request.getSearchStrategy());
        entity.setDocCount(0);
        entity.setOwnerId(ownerId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        kbMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public KnowledgeBaseVO update(String id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase entity = kbMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getChunkStrategy() != null) entity.setChunkStrategyJson(JsonUtils.toJson(request.getChunkStrategy()));
        if (request.getSearchStrategy() != null) entity.setSearchStrategy(request.getSearchStrategy());
        entity.setUpdatedAt(LocalDateTime.now());
        kbMapper.update(entity);
        return toVO(entity);
    }

    @Override
    @Transactional
    public void delete(String id) {
        KnowledgeBase entity = kbMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.KM_KB_001);
        kbMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<String> ids) {
        kbMapper.batchDeleteByIds(ids);
    }

    @Override
    public KnowledgeBaseVO getById(String id) {
        KnowledgeBase entity = kbMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.KM_KB_001);
        return toVO(entity);
    }

    @Override
    public PageResult<KnowledgeBaseVO> list(String keyword, String docType, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<KnowledgeBase> list = kbMapper.list(keyword, docType, offset, pageSize);
        long total = kbMapper.count(keyword, docType);
        List<KnowledgeBaseVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(voList, total, page, pageSize);
    }

    @SuppressWarnings("unchecked")
    private KnowledgeBaseVO toVO(KnowledgeBase entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setDocType(entity.getDocType());
        try {
            vo.setChunkStrategy(JsonUtils.toJson(entity.getChunkStrategyJson()));
        } catch (Exception e) {
            vo.setChunkStrategy(entity.getChunkStrategyJson());
        }
        vo.setSearchStrategy(entity.getSearchStrategy());
        vo.setDocCount(entity.getDocCount());
        vo.setOwnerId(entity.getOwnerId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
