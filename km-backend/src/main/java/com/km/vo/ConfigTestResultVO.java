package com.km.vo;

import lombok.Data;

@Data
public class ConfigTestResultVO {

    private boolean success;
    private String message;
    private Long latencyMs;
}
