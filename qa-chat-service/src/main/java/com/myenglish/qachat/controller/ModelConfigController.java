package com.myenglish.qachat.controller;

import com.myenglish.qachat.constant.SessionConstants;
import com.myenglish.qachat.dto.req.SaveModelConfigReq;
import com.myenglish.qachat.dto.resp.ModelConfigVO;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qacommon.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型配置管理接口（前端调用，当前阶段用默认 userId）
 */
@RestController
@RequestMapping("/api/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list() {
        return ApiResponse.success(modelConfigService.listByUser(SessionConstants.DEFAULT_USER_ID));
    }

    @PostMapping
    public ApiResponse<ModelConfigVO> create(@Valid @RequestBody SaveModelConfigReq req) {
        return ApiResponse.success(modelConfigService.create(SessionConstants.DEFAULT_USER_ID, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(@PathVariable Long id, @Valid @RequestBody SaveModelConfigReq req) {
        return ApiResponse.success(modelConfigService.update(SessionConstants.DEFAULT_USER_ID, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(SessionConstants.DEFAULT_USER_ID, id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/default")
    public ApiResponse<Void> setDefault(@PathVariable Long id) {
        modelConfigService.setDefault(SessionConstants.DEFAULT_USER_ID, id);
        return ApiResponse.success(null);
    }
}
