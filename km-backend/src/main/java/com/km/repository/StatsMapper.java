package com.km.repository;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface StatsMapper {
    long countKnowledgeBases();
    long countDocuments();
    long countChunks();
    List<Map<String, Object>> dailyUploadTrend(@Param("days") int days);
}
