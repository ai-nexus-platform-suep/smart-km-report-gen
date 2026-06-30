package com.km.service;

import com.km.dto.response.StatsSummaryVO;
import com.km.dto.response.KbStatsVO;

public interface StatsService {
    StatsSummaryVO getSummary();
    KbStatsVO getKbStats(String kbId);
}
