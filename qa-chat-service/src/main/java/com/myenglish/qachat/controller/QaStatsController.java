package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.resp.QaStatsOverviewVO;
import com.myenglish.qachat.service.QaStatsService;
import com.myenglish.qacommon.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats/qa")
@RequiredArgsConstructor
public class QaStatsController {

    private final QaStatsService qaStatsService;

    /**
     * 知识问答统计概览：总次数 + 近 30 天趋势
     */
    @GetMapping("/overview")
    public ApiResponse<QaStatsOverviewVO> overview() {
        return ApiResponse.success(qaStatsService.getOverview());
    }
}
