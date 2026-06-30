package com.km.dto.request;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

@Data
public class ParserConfigRequest {

    @Pattern(regexp = "tika|native", message = "解析后端仅支持 tika 或 native")
    private String backend;

    @Min(value = 1, message = "最大并发数不能小于 1")
    @Max(value = 10, message = "最大并发数不能大于 10")
    private Integer maxConcurrency;
}
