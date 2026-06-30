package com.km.repository;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface StatsMapper {
    long countKnowledgeBases();
    long countDocuments();
    long countChunks();
    long countReadyDocuments();
    long countProcessingDocuments();
    long countFailedDocuments();
    long countKbDocuments(String kbId);
    long countKbChunks(String kbId);
    String getKbNameById(String kbId);
    List<Map<String, Object>> dailyUploadTrend(@Param("days") int days);
}
