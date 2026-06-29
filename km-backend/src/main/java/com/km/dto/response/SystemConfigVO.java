package com.km.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public class SystemConfigVO {
    private String configKey;
    private Map<String, Object> configValue;
    private LocalDateTime updatedAt;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public Map<String, Object> getConfigValue() { return configValue; }
    public void setConfigValue(Map<String, Object> configValue) { this.configValue = configValue; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
