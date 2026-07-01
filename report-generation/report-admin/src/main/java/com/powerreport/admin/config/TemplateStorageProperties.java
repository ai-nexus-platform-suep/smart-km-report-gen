package com.powerreport.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.template")
public class TemplateStorageProperties {

    private String storageDir = "./storage/templates";
}
