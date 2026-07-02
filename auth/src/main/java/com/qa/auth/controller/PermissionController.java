package com.qa.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qa.auth.entity.SysPermissionEntity;
import com.qa.auth.mapper.SysPermissionMapper;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final SysPermissionMapper sysPermissionMapper;

    @GetMapping
    @RequirePermission("auth:permission:manage")
    public ApiResponse<List<SysPermissionEntity>> list() {
        List<SysPermissionEntity> list = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermissionEntity>()
                        .eq(SysPermissionEntity::getEnabled, true)
                        .orderByAsc(SysPermissionEntity::getSortOrder));
        return ApiResponse.success(list);
    }
}
