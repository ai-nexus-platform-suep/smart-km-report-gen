package com.km.service;

import com.km.common.dto.PageResult;
import com.km.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DocumentService {
    DocumentUploadResultVO upload(String kbId, MultipartFile file, String tags, Long createdBy);
    DocumentVO getById(String id);
    PageResult<DocumentVO> listByKbId(String kbId, String status, int page, int pageSize);
    DocumentDeleteResultVO delete(String kbId, String docId);
    DocumentBatchDeleteResultVO batchDelete(String kbId, List<String> ids);
    DocumentVO updateTags(String id, Map<String, String> tags);
    DocumentVO retryProcess(String id);
    PageResult<?> listChunks(String docId, int page, int pageSize);
}
