package com.km.controller.config;

import com.km.common.dto.ApiResponse;
import com.km.dto.response.SystemConfigVO;
import com.km.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ApiResponse<List<SystemConfigVO>> list() {
        return ApiResponse.ok(systemConfigService.findAll());
    }

    @GetMapping("/{key}")
    public ApiResponse<SystemConfigVO> get(@PathVariable("key") String key) {
        return ApiResponse.ok(systemConfigService.findByKey(key));
    }

    @PutMapping("/{key}")
    public ApiResponse<SystemConfigVO> update(
            @PathVariable("key") String key,
            @RequestBody Map<String, Object> value) {
        return ApiResponse.ok(systemConfigService.update(key, value));
    }
}
