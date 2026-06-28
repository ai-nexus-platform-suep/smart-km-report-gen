package com.km.controller;

import com.km.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("service", "km-backend");
        data.put("status", "UP");
        data.put("version", "1.0.0-SNAPSHOT");
        return ApiResponse.ok(data);
    }
}
