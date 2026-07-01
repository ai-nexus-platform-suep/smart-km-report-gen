package com.qa.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qa.auth.dto.response.SysLogVO;
import com.qa.auth.service.SysLogService;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/logs")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService sysLogService;

    @GetMapping
    @RequirePermission("auth:log:list")
    public ApiResponse<Page<SysLogVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module) {
        return ApiResponse.success(sysLogService.page(pageNum, pageSize, username, module));
    }
}
