package com.km.controller;

import com.km.common.dto.ApiResponse;
import com.km.dto.response.StatsSummaryVO;
import com.km.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    public ApiResponse<StatsSummaryVO> summary() {
        return ApiResponse.ok(statsService.getSummary());
    }
}
