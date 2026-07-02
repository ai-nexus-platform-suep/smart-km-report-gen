package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 前端视图：apiKey 脱敏 */
@Data
@Builder
public class ModelConfigVO {

    private Long id;
    private Long userId;
    private String provider;
    private String baseUrl;
    private String modelName;
    private String apiKeyMasked;
    private String scenario;
    private Integer enabled;
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
