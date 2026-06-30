package com.powerreport.admin.controller;

import com.myenglish.qacommon.dto.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminHealthController {

    @GetMapping("/api/admin/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("service", "report-admin", "status", "UP"));
    }
}
