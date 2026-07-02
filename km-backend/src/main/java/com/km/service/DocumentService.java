package com.km.service;

import com.km.common.dto.PageResult;
import com.km.dto.request.ReplaceDocumentChunksRequest;
import com.km.dto.response.DocumentBatchDeleteResponse;
import com.km.dto.response.DocumentDeleteResponse;
import com.km.dto.response.DocumentUploadResponse;
import com.km.dto.response.ReplaceDocumentChunksResponse;
import com.km.vo.ChunkVO;
import com.km.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 文档管理服务接口
 */
public interface DocumentService {

    /**
     * 上传文档到知识库
     */
    DocumentUploadResponse uploadDocument(String kbId, MultipartFile file, String tagsJson, Long userId);

    /**
     * 查询知识库内的文档列表（分页）
     */
    PageResult<DocumentVO> listDocuments(String kbId, String status, int page, int pageSize);

    /**
     * 获取文档元数据
     */
    DocumentVO getDocument(String docId);

    /**
     * 删除知识库内的单个文档
     */
    DocumentDeleteResponse deleteDocument(String kbId, String docId);

    /**
     * 批量删除知识库内的文档
     */
    DocumentBatchDeleteResponse batchDeleteDocuments(String kbId, java.util.List<String> ids);

    /**
     * 获取文档切片列表（分页）
     */
    PageResult<ChunkVO> listChunks(String docId, int page, int pageSize);

    /**
     * 下载原始文件
     */
    void downloadDocument(String docId, HttpServletResponse response);

    /**
     * 重试失败的文档处理
     */
    DocumentVO retryProcess(String docId);

    /**
     * 更新文档标签
     */
    DocumentVO updateTags(String docId, Map<String, String> tags);

    /**
     * 更新文档状态
     */
    void updateStatus(String docId, String status, String errorMsg);

    /**
     * 替换文档切片索引（供 AI Worker 内部调用）。
     */
    ReplaceDocumentChunksResponse replaceChunks(String docId, ReplaceDocumentChunksRequest request);
}
