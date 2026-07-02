package com.km.dto.request;

import lombok.Data;

@Data
public class EmbeddingConfigRequest {

    private String modelName;
    private String apiUrl;
    /** 若提交脱敏值（含 ****）则保留库中原 Key */
    private String apiKey;
    private Integer dimension;
}
