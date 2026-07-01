package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.resp.QaStatsOverviewVO;
import com.myenglish.qachat.service.QaStatsService;
import com.myenglish.qacommon.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识问答统计接口
 *
 * <p>提供 QA 知识问答的统计数据查询。
 *
 */
@Slf4j
@RestController
@RequestMapping("/api/stats/qa")
@RequiredArgsConstructor
public class QaStatsController {

    private final QaStatsService qaStatsService;

    /**
     * 知识问答统计概览
     *
     * <p>返回平台整体的知识问答使用统计，包含历史总次数和近 30 天的每日趋势数据。
     *
     * @return 统计概览（totalCount + dailyTrends）
     */
    @GetMapping("/overview")
    public ApiResponse<QaStatsOverviewVO> overview() {
        log.info("查询 QA 统计概览");
        return ApiResponse.success(qaStatsService.getOverview());
    }
}
