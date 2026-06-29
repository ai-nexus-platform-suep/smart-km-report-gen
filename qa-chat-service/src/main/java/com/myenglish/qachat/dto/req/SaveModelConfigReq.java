package com.myenglish.qachat.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveModelConfigReq {

    @NotBlank
    private String provider;

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String modelName;

    @NotBlank
    private String apiKey;

    private String scenario = "chat";

    private Integer enabled = 1;
}
