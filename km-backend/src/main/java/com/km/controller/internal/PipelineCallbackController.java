package com.km.controller.internal;

import com.km.common.constant.DocumentStatus;
import com.km.common.dto.ApiResponse;
import com.km.dto.request.DocumentStatusCallbackRequest;
import com.km.entity.Chunk;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoint called by Python km-ai-service to report document processing status.
 */
@RestController
@RequestMapping("/internal")
public class PipelineCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PipelineCallbackController.class);

    private final DocumentMapper docMapper;
    private final ChunkMapper chunkMapper;

    public PipelineCallbackController(DocumentMapper docMapper, ChunkMapper chunkMapper) {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
    }

    @PostMapping("/document/status")
    @Transactional
    public ApiResponse<Void> updateStatus(@RequestBody DocumentStatusCallbackRequest request) {
        log.info("Status callback: docId={}, status={}, chunks={}",
                request.getDocumentId(), request.getStatus(),
                request.getChunks() != null ? request.getChunks().size() : 0);

        String docId = request.getDocumentId();
        String status = request.getStatus();
        String errorMsg = request.getErrorMsg();

        // Update document status
        docMapper.updateStatus(docId, status, errorMsg);

        // If READY, insert chunks
        if (DocumentStatus.READY.equals(status) && request.getChunks() != null) {
            List<Chunk> chunks = new ArrayList<>();
            for (Map<String, Object> c : request.getChunks()) {
                Chunk chunk = new Chunk();
                chunk.setId((String) c.get("id"));
                chunk.setDocId((String) c.get("doc_id"));
                chunk.setContent((String) c.get("content"));
                chunk.setChapterPath((String) c.getOrDefault("chapter_path", ""));
                chunk.setChunkIndex(((Number) c.getOrDefault("chunk_index", 0)).intValue());
                chunk.setChunkType((String) c.getOrDefault("chunk_type", "paragraph"));
                chunk.setVectorId((String) c.getOrDefault("vector_id", ""));
                Object cc = c.get("char_count");
                chunk.setCharCount(cc != null ? ((Number) cc).intValue() : ((String) c.get("content")).length());
                chunk.setCreatedAt(LocalDateTime.now());
                chunks.add(chunk);
            }
            if (!chunks.isEmpty()) {
                chunkMapper.batchInsert(chunks);
            }
        }

        return ApiResponse.ok();
    }
}
