package com.km.service.impl;

import com.km.dto.response.StatsSummaryVO;
import com.km.repository.StatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StatsServiceImpl 单元测试。
 * 覆盖：统计摘要查询、零值处理、空趋势处理、字段映射正确性。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    @Mock
    private StatsMapper statsMapper;

    private StatsServiceImpl statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsServiceImpl(statsMapper);
    }

    @Test
    void shouldReturnSummary() {
        when(statsMapper.countKnowledgeBases()).thenReturn(10L);
        when(statsMapper.countDocuments()).thenReturn(200L);
        when(statsMapper.countChunks()).thenReturn(5000L);
        when(statsMapper.countReadyDocuments()).thenReturn(180L);
        List<Map<String, Object>> trend = createDailyTrend();
        when(statsMapper.dailyUploadTrend(30)).thenReturn(trend);

        StatsSummaryVO vo = statsService.getSummary();

        assertNotNull(vo);
        assertEquals(10L, vo.getKnowledgeBaseCount());
        assertEquals(200L, vo.getDocumentCount());
        assertEquals(5000L, vo.getChunkCount());
        assertEquals(180L, vo.getReadyDocumentCount());
        assertNotNull(vo.getDailyUploadTrend());
        assertEquals(2, vo.getDailyUploadTrend().size());
    }

    @Test
    void shouldReturnZeroCounts() {
        when(statsMapper.countKnowledgeBases()).thenReturn(0L);
        when(statsMapper.countDocuments()).thenReturn(0L);
        when(statsMapper.countChunks()).thenReturn(0L);
        when(statsMapper.countReadyDocuments()).thenReturn(0L);
        when(statsMapper.dailyUploadTrend(anyInt())).thenReturn(Collections.emptyList());

        StatsSummaryVO vo = statsService.getSummary();

        assertNotNull(vo);
        assertEquals(0L, vo.getKnowledgeBaseCount());
        assertEquals(0L, vo.getDocumentCount());
        assertEquals(0L, vo.getChunkCount());
        assertEquals(0L, vo.getReadyDocumentCount());
        assertNotNull(vo.getDailyUploadTrend());
        assertTrue(vo.getDailyUploadTrend().isEmpty());
    }

    @Test
    void shouldHandleNullDailyTrend() {
        when(statsMapper.countKnowledgeBases()).thenReturn(5L);
        when(statsMapper.countDocuments()).thenReturn(50L);
        when(statsMapper.countChunks()).thenReturn(1000L);
        when(statsMapper.countReadyDocuments()).thenReturn(45L);
        when(statsMapper.dailyUploadTrend(anyInt())).thenReturn(null);

        StatsSummaryVO vo = statsService.getSummary();

        assertNotNull(vo);
        assertEquals(5L, vo.getKnowledgeBaseCount());
        assertNull(vo.getDailyUploadTrend());
    }

    @Test
    void shouldCorrectlyMapFields() {
        when(statsMapper.countKnowledgeBases()).thenReturn(3L);
        when(statsMapper.countDocuments()).thenReturn(30L);
        when(statsMapper.countChunks()).thenReturn(300L);
        when(statsMapper.countReadyDocuments()).thenReturn(25L);
        when(statsMapper.dailyUploadTrend(anyInt())).thenReturn(null);

        StatsSummaryVO vo = statsService.getSummary();

        // 验证各字段来自对应的 Mapper 方法，没有交叉
        assertEquals(3L, vo.getKnowledgeBaseCount());
        assertEquals(30L, vo.getDocumentCount());
        assertEquals(300L, vo.getChunkCount());
        assertEquals(25L, vo.getReadyDocumentCount());
        verify(statsMapper).countKnowledgeBases();
        verify(statsMapper).countDocuments();
        verify(statsMapper).countChunks();
        verify(statsMapper).countReadyDocuments();
        verify(statsMapper).dailyUploadTrend(30);
    }

    // ====== 辅助方法 ======

    private List<Map<String, Object>> createDailyTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();

        Map<String, Object> day1 = new HashMap<>();
        day1.put("date", "2026-06-28");
        day1.put("count", 15L);
        trend.add(day1);

        Map<String, Object> day2 = new HashMap<>();
        day2.put("date", "2026-06-29");
        day2.put("count", 22L);
        trend.add(day2);

        return trend;
    }
}
