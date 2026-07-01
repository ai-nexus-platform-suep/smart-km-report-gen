package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.req.SaveModelConfigReq;
import com.myenglish.qachat.dto.resp.ModelConfigVO;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qacommon.context.UserContextHolder;
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
 *
 * 提供模型配置的完整 CRUD 及默认配置管理功能。
 * 用户身份由网关通过 JWT 解析后注入请求头，经 {@link UserContextHolder} 获取。
 */
@Slf4j
@RestController
@RequestMapping("/api/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    /**
     * 查询当前用户的模型配置列表
     *
     * @return 当前用户的所有模型配置（按创建时间倒序）
     */
    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list() {
        log.info("查询模型配置列表 userId={}", UserContextHolder.getUserId());
        Long userId = UserContextHolder.getUserId();
        return ApiResponse.success(modelConfigService.listByUser(userId));
    }

    /**
     * 新增模型配置
     *
     * 若该场景下尚无其他配置，自动设为默认配置。
     *
     * @param req 模型配置请求体（provider、baseUrl、modelName、apiKey、scenario 等）
     * @return 创建成功的模型配置（含加密掩码后的 API Key）
     */
    @PostMapping
    public ApiResponse<ModelConfigVO> create(
            @Valid @RequestBody SaveModelConfigReq req) {
        Long userId = UserContextHolder.getUserId();
        log.info("新增模型配置 userId={} provider={} model={}", userId, req.getProvider(), req.getModelName());
        return ApiResponse.success(modelConfigService.create(userId, req));
    }

    /**
     * 修改指定模型配置
     *
     * <p>仅允许修改当前用户自己的配置，否则返回 403。
     *
     * @param id  配置 ID
     * @param req 模型配置请求体（apiKey 为空时保留原值）
     * @return 更新后的模型配置
     */
    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveModelConfigReq req) {
        Long userId = UserContextHolder.getUserId();
        log.info("修改模型配置 userId={} id={}", userId, id);
        return ApiResponse.success(modelConfigService.update(userId, id, req));
    }

    /**
     * 删除指定模型配置
     *
     * <p>仅允许删除当前用户自己的配置，否则返回 403。
     *
     * @param id 配置 ID
     * @return 空响应（成功）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        log.info("删除模型配置 userId={} id={}", userId, id);
        modelConfigService.delete(userId, id);
        return ApiResponse.success(null);
    }

    /**
     * 将指定配置设为默认配置
     *
     * <p>同一场景下只有一条默认配置，设置时会自动取消该场景下原有的默认配置。
     * 仅允许操作当前用户自己的配置。
     *
     * @param id 配置 ID
     * @return 空响应（成功）
     */
    @PostMapping("/{id}/default")
    public ApiResponse<Void> setDefault(
            @PathVariable Long id) {
        Long userId = UserContextHolder.getUserId();
        log.info("设为默认配置 userId={} id={}", userId, id);
        modelConfigService.setDefault(userId, id);
        return ApiResponse.success(null);
    }
}
