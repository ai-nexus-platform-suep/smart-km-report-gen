package com.km.service.impl;

import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.response.StatsSummaryVO;
import com.km.repository.StatsMapper;
import com.km.service.StatsService;
import com.km.dto.response.KbStatsVO;
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
        String kbName = statsMapper.getKbNameById(kbId);
        if (kbName == null) {
            throw new BusinessException(ErrorCode.KM_KB_001);
        }
        KbStatsVO vo = new KbStatsVO();
        vo.setKbId(kbId);
        vo.setKbName(kbName);
        vo.setDocumentCount(statsMapper.countKbDocuments(kbId));
        vo.setChunkCount(statsMapper.countKbChunks(kbId));
        return vo;
    }
}
