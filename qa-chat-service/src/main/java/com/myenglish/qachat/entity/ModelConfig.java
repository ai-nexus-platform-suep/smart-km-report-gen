package com.myenglish.qachat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_config")
public class ModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String provider;

    private String baseUrl;

    private String modelName;

    private String apiKeyEncrypted;

    private String scenario;

    private Integer enabled;

    private Integer isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
