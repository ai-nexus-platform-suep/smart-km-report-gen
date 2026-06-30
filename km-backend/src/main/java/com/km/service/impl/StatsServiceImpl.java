package com.km.service.impl;

import com.km.dto.response.StatsSummaryVO;
import com.km.repository.StatsMapper;
import com.km.service.StatsService;
import com.km.vo.KbStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsMapper statsMapper;

    @Override
    @Cacheable(value="statsSummary", unless="#result==null")
    public StatsSummaryVO getSummary() {
        StatsSummaryVO vo = new StatsSummaryVO();
        vo.setKnowledgeBaseCount(statsMapper.countKnowledgeBases());
        vo.setDocumentCount(statsMapper.countDocuments());
        vo.setChunkCount(statsMapper.countChunks());
        vo.setReadyDocumentCount(statsMapper.countReadyDocuments());
        vo.setProcessingDocumentCount(statsMapper.countProcessingDocuments());
        vo.setFailedDocumentCount(statsMapper.countFailedDocuments());
        vo.setDailyUploadTrend(statsMapper.dailyUploadTrend(30));
        return vo;
    }

    @Override
    public KbStatsVO getKbStats(String kbId) {
        KbStatsVO vo = new KbStatsVO();
        vo.setKbId(kbId);
        vo.setKbName(statsMapper.getKbNameById(kbId));
        vo.setDocumentCount(statsMapper.countKbDocuments(kbId));
        vo.setChunkCount(statsMapper.countKbChunks(kbId));
        return vo;
    }
}
