package com.km.service;

import com.km.dto.response.StatsSummaryVO;

public interface StatsService {
    StatsSummaryVO getSummary();
    com.km.vo.KbStatsVO getKbStats(String kbId);
}
