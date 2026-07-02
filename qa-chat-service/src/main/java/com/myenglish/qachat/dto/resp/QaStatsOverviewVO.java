package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QaStatsOverviewVO {

    /** 知识问答总次数（已完成 assistant 回答） */
    private long totalCount;

    /** 近 30 天每日趋势，含无数据的日期（count=0） */
    private List<DailyTrendVO> trend;
}
