package com.myenglish.qachat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.myenglish.qachat.dto.req.SaveModelConfigReq;
import com.myenglish.qachat.dto.resp.ModelConfigInternalVO;
import com.myenglish.qachat.dto.resp.ModelConfigVO;
import com.myenglish.qachat.entity.ModelConfig;
import com.myenglish.qachat.exception.BusinessException;
import com.myenglish.qachat.mapper.ModelConfigMapper;
import com.myenglish.qachat.service.ModelConfigService;
import com.myenglish.qachat.util.AesUtil;
import com.myenglish.qacommon.dto.ApiCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;

    @Value("${model-config.aes-key}")
    private String aesKey;

    @Value("${model-config.default-timeout-seconds:60}")
    private int defaultTimeoutSeconds;

    @Override
    public List<ModelConfigVO> listByUser(Long userId) {
        log.info("开始查询");
        List<ModelConfig> configs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getUserId, userId)
                        .orderByDesc(ModelConfig::getCreatedAt));
        log.info("查询结束 {}",configs);
        return configs.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO create(Long userId, SaveModelConfigReq req) {
        ModelConfig config = new ModelConfig();
        config.setUserId(userId);
        config.setProvider(req.getProvider());
        config.setBaseUrl(req.getBaseUrl());
        config.setModelName(req.getModelName());
        config.setApiKeyEncrypted(AesUtil.encrypt(req.getApiKey(), aesKey));
        config.setScenario(req.getScenario());
        config.setEnabled(req.getEnabled());

        // 如果该场景下没有其他配置，自动设为默认
        long count = modelConfigMapper.selectCount(
                new LambdaQueryWrapper<ModelConfig>()
                        .eq(ModelConfig::getUserId, userId)
                        .eq(ModelConfig::getScenario, req.getScenario()));
        config.setIsDefault(count == 0 ? 1 : 0);

        modelConfigMapper.insert(config);
        log.info("创建模型配置 userId={} id={} provider={} scenario={}", userId, config.getId(), req.getProvider(), req.getScenario());
        return toVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO update(Long userId, Long configId, SaveModelConfigReq req) {
        ModelConfig config = getOwnConfig(userId, configId);

        config.setProvider(req.getProvider());
        config.setBaseUrl(req.getBaseUrl());
        config.setModelName(req.getModelName());
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            config.setApiKeyEncrypted(AesUtil.encrypt(req.getApiKey(), aesKey));
        }
        config.setScenario(req.getScenario());
        config.setEnabled(req.getEnabled());

        modelConfigMapper.updateById(config);
        return toVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long configId) {
        ModelConfig config = getOwnConfig(userId, configId);
        modelConfigMapper.deleteById(config.getId());
        log.info("删除模型配置 userId={} id={}", userId, configId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long configId) {
        ModelConfig config = getOwnConfig(userId, configId);

        // 将该场景下其他默认配置取消
        modelConfigMapper.update(null,
                new LambdaUpdateWrapper<ModelConfig>()
                        .eq(ModelConfig::getUserId, userId)
                        .eq(ModelConfig::getScenario, config.getScenario())
                        .eq(ModelConfig::getIsDefault, 1)
                        .set(ModelConfig::getIsDefault, 0));

        // 设置当前为默认
        config.setIsDefault(1);
        modelConfigMapper.updateById(config);
        log.info("设为默认配置 userId={} id={} scenario={}", userId, configId, config.getScenario());
    }

    @Override
    public ModelConfigInternalVO getDefaultDecrypted(Long userId, String scenario) {
        ModelConfig config = modelConfigMapper.selectDefault(userId, scenario);
        if (config == null) {
            throw new BusinessException(ApiCode.DATA_NOT_FOUND, "未找到默认模型配置");
        }

        return ModelConfigInternalVO.builder()
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .apiKey(AesUtil.decrypt(config.getApiKeyEncrypted(), aesKey))
                .timeoutSeconds(defaultTimeoutSeconds)
                .build();
    }

    private ModelConfig getOwnConfig(Long userId, Long configId) {
        ModelConfig config = modelConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(ApiCode.DATA_NOT_FOUND, "配置不存在");
        }
        if (!config.getUserId().equals(userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作此配置");
        }
        return config;
    }

    private ModelConfigVO toVO(ModelConfig config) {
        String maskedKey;
        try {
            String plainKey = AesUtil.decrypt(config.getApiKeyEncrypted(), aesKey);
            maskedKey = AesUtil.maskApiKey(plainKey);
        } catch (Exception e) {
            maskedKey = "****";
        }
        return ModelConfigVO.builder()
                .id(config.getId())
                .userId(config.getUserId())
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .apiKeyMasked(maskedKey)
                .scenario(config.getScenario())
                .enabled(config.getEnabled())
                .isDefault(config.getIsDefault())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
