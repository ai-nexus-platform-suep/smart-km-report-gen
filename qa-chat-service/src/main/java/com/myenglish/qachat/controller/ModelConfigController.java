package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.req.SaveModelConfigReq;
import com.myenglish.qachat.dto.resp.ModelConfigVO;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qacommon.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型配置管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list(@RequestHeader("X-User-Id") Long userId) {
        log.info("查询模型配置列表 userId={}", userId);
        return ApiResponse.success(modelConfigService.listByUser(userId));
    }

    @PostMapping
    public ApiResponse<ModelConfigVO> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SaveModelConfigReq req) {
        log.info("新增模型配置 userId={} provider={} model={}", userId, req.getProvider(), req.getModelName());
        return ApiResponse.success(modelConfigService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SaveModelConfigReq req) {
        log.info("修改模型配置 userId={} id={}", userId, id);
        return ApiResponse.success(modelConfigService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        log.info("删除模型配置 userId={} id={}", userId, id);
        modelConfigService.delete(userId, id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/default")
    public ApiResponse<Void> setDefault(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        log.info("设为默认配置 userId={} id={}", userId, id);
        modelConfigService.setDefault(userId, id);
        return ApiResponse.success(null);
    }
}
