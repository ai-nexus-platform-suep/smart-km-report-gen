package com.myenglish.qachat.service;

import com.myenglish.qachat.dto.req.SaveModelConfigReq;
import com.myenglish.qachat.dto.resp.ModelConfigVO;
import com.myenglish.qachat.dto.resp.ModelConfigInternalVO;

import java.util.List;

public interface ModelConfigService {

    /** 查询用户配置列表（apiKey 脱敏） */
    List<ModelConfigVO> listByUser(Long userId);

    /** 新增配置（apiKey 加密落库） */
    ModelConfigVO create(Long userId, SaveModelConfigReq req);

    /** 修改配置 */
    ModelConfigVO update(Long userId, Long configId, SaveModelConfigReq req);

    /** 删除配置 */
    void delete(Long userId, Long configId);

    /** 设为默认（事务切换同场景其他默认） */
    void setDefault(Long userId, Long configId);

    /** 获取默认配置（内部接口，apiKey 解密） */
    ModelConfigInternalVO getDefaultDecrypted(Long userId, String scenario);
}
