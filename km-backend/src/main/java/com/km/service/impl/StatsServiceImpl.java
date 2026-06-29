package com.km.service.impl;

import com.km.dto.response.StatsSummaryVO;
import com.km.repository.StatsMapper;
import com.km.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsMapper statsMapper;

    @Override
    public StatsSummaryVO getSummary() {
        StatsSummaryVO vo = new StatsSummaryVO();
        vo.setKbCount(statsMapper.countKnowledgeBases());
        vo.setDocCount(statsMapper.countDocuments());
        vo.setChunkCount(statsMapper.countChunks());
        vo.setDailyUploadTrend(statsMapper.dailyUploadTrend(30));
        return vo;
    }
}
