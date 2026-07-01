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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final List<String> ALLOWED_DOC_TYPES = Arrays.asList("规程规范", "技术报告论文", "术语条目", "通用文档");
    private static final List<String> ALLOWED_SEARCH_STRATEGIES = Arrays.asList("vector", "vector_rerank");

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public PageResult<KnowledgeBaseVO> list(String docType, String keyword, int page, int pageSize) {
        validatePage(page, pageSize);
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
        validateDocType(request.getDocType());
        kb.setDocType(request.getDocType());
        validateChunkStrategy(request.getChunkStrategy());
        kb.setChunkStrategyJson(JsonUtils.toJson(request.getChunkStrategy()));
        validateSearchStrategy(request.getSearchStrategy());
        kb.setSearchStrategy(request.getSearchStrategy());
        kb.setOwnerId(ownerId);
        knowledgeBaseMapper.insert(kb);
        return getById(kb.getId());
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
        if (request.getDocType() != null) {
            validateDocType(request.getDocType());
            kb.setDocType(request.getDocType());
        }
        if (request.getChunkStrategy() != null) {
            validateChunkStrategy(request.getChunkStrategy());
            kb.setChunkStrategyJson(JsonUtils.toJson(request.getChunkStrategy()));
        }
        if (request.getSearchStrategy() != null) {
            validateSearchStrategy(request.getSearchStrategy());
            kb.setSearchStrategy(request.getSearchStrategy());
        }
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
        List<String> uniqueIds = normalizeIds(ids);
        for (String id : uniqueIds) {
            KnowledgeBase existing = knowledgeBaseMapper.getById(id);
            if (existing == null) {
                throw new BusinessException(ErrorCode.KM_KB_001);
            }
        }
        knowledgeBaseMapper.batchDelete(uniqueIds);
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setDocType(kb.getDocType());
        vo.setDocCount(kb.getDocCount());
        vo.setChunkStrategy(parseChunkStrategy(kb.getChunkStrategyJson()));
        vo.setSearchStrategy(kb.getSearchStrategy());
        vo.setOwnerId(kb.getOwnerId());
        vo.setOwnerName(kb.getOwnerName());
        vo.setCreatedAt(kb.getCreatedAt());
        vo.setUpdatedAt(kb.getUpdatedAt());
        return vo;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "pageSize must be between 1 and 100");
        }
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ids cannot be empty");
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private void validateChunkStrategy(Map<String, Object> chunkStrategy) {
        if (chunkStrategy == null || !chunkStrategy.containsKey("type")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunkStrategy.type is required");
        }
        Object type = chunkStrategy.get("type");
        if (!("heading".equals(type) || "fixed_size".equals(type))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunkStrategy.type must be heading or fixed_size");
        }
        validateIntegerRange(chunkStrategy.get("chunkSize"), "chunkStrategy.chunkSize", 128, 2048);
        validateIntegerRange(chunkStrategy.get("overlap"), "chunkStrategy.overlap", 0, 256);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseChunkStrategy(String chunkStrategyJson) {
        if (chunkStrategyJson == null || chunkStrategyJson.trim().isEmpty()) {
            return null;
        }
        try {
            Object value = JsonUtils.fromJson(chunkStrategyJson, Object.class);
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            if (value instanceof String) {
                return legacyChunkStrategy((String) value);
            }
        } catch (IllegalStateException ex) {
            return legacyChunkStrategy(chunkStrategyJson);
        }
        return null;
    }

    private Map<String, Object> legacyChunkStrategy(String value) {
        String type = value;
        if (type != null && type.startsWith("\"") && type.endsWith("\"") && type.length() >= 2) {
            type = type.substring(1, type.length() - 1);
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("type", type);
        return fallback;
    }

    private void validateDocType(String docType) {
        if (!ALLOWED_DOC_TYPES.contains(docType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported docType: " + docType);
        }
    }

    private void validateSearchStrategy(String searchStrategy) {
        if (!ALLOWED_SEARCH_STRATEGIES.contains(searchStrategy)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "searchStrategy must be vector or vector_rerank");
        }
    }

    private void validateIntegerRange(Object value, String field, int min, int max) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " must be a number");
        }
        int intValue = ((Number) value).intValue();
        if (intValue < min || intValue > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " must be between " + min + " and " + max);
        }
    }
}
