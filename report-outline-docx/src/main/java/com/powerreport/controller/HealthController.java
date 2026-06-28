package com.powerreport.controller;

import com.powerreport.common.ApiResult;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResult<Map<String, String>> health() {
        return ApiResult.ok(Map.of(
                "status", "ok",
                "scope", "outline-and-docx"
        ));
    }
}
