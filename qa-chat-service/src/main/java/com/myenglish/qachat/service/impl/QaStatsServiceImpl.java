package com.myenglish.qachat.service.impl;

import com.myenglish.qachat.constant.SessionConstants;
import com.myenglish.qachat.dto.resp.DailyTrendVO;
import com.myenglish.qachat.dto.resp.QaStatsOverviewVO;
import com.myenglish.qachat.mapper.MessageMapper;
import com.myenglish.qachat.mapper.dto.DailyCountRow;
import com.myenglish.qachat.service.QaStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaStatsServiceImpl implements QaStatsService {

    private final MessageMapper messageMapper;

    @Override
    public QaStatsOverviewVO getOverview() {
        long start = System.currentTimeMillis();
        long totalCount = messageMapper.countKnowledgeQa(
                SessionConstants.STATUS_ACTIVE,
                SessionConstants.ROLE_ASSISTANT,
                SessionConstants.INTENT_KNOWLEDGE_QA,
                SessionConstants.GENERATE_STATUS_COMPLETED);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(SessionConstants.TREND_DAYS - 1L);
        LocalDateTime startAt = startDate.atStartOfDay();

        List<DailyCountRow> rows = messageMapper.selectDailyKnowledgeQaCount(
                SessionConstants.STATUS_ACTIVE,
                SessionConstants.ROLE_ASSISTANT,
                SessionConstants.INTENT_KNOWLEDGE_QA,
                SessionConstants.GENERATE_STATUS_COMPLETED,
                startAt);

        Map<LocalDate, Long> countByDate = rows.stream()
                .collect(Collectors.toMap(DailyCountRow::getStatDate, DailyCountRow::getCount));

        List<DailyTrendVO> trend = new ArrayList<>(SessionConstants.TREND_DAYS);
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            trend.add(DailyTrendVO.builder()
                    .date(date)
                    .count(countByDate.getOrDefault(date, 0L))
                    .build());
        }

        QaStatsOverviewVO result = QaStatsOverviewVO.builder()
                .totalCount(totalCount)
                .trend(trend)
                .build();
        log.info("QA 统计查询完成 total={} 耗时={}ms", totalCount, System.currentTimeMillis() - start);
        return result;
    }
}
