package com.km.controller.config;

import com.km.common.dto.ApiResponse;
import com.km.dto.response.StatsSummaryVO;
import com.km.repository.ChunkMapper;
import com.km.repository.DocumentMapper;
import com.km.repository.KnowledgeBaseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper docMapper;
    private final ChunkMapper chunkMapper;

    public StatsController(KnowledgeBaseMapper kbMapper, DocumentMapper docMapper, ChunkMapper chunkMapper) {
        this.kbMapper = kbMapper;
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
    }

    @GetMapping("/summary")
    public ApiResponse<StatsSummaryVO> summary() {
        StatsSummaryVO vo = new StatsSummaryVO();
        // Simplified: these counts come from DB queries
        // For MVP we use direct counts; production would use aggregation queries
        long kbCount = kbMapper.count(null, null);
        long docCount = docMapper.countAll();
        long chunkTotal = chunkMapper.countAll();
        long readyCount = docMapper.countByStatus("READY");
        vo.setKnowledgeBaseCount((int) kbCount);
        vo.setDocumentCount((int) docCount);
        vo.setChunkCount((int) chunkTotal);
        vo.setReadyDocumentCount((int) readyCount);
        return ApiResponse.ok(vo);
    }
}
