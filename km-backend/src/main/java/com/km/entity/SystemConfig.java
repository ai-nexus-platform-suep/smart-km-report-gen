package com.km.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemConfig {

    private String configKey;
    private String configValue;
    private LocalDateTime updatedAt;
}
