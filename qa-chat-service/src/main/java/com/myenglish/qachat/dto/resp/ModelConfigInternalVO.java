package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

/** 内部接口视图：apiKey 明文（仅供 Python 调用） */
@Data
@Builder
public class ModelConfigInternalVO {

    private String provider;
    private String baseUrl;
    private String modelName;
    private String apiKey;
    private Integer timeoutSeconds;
}
