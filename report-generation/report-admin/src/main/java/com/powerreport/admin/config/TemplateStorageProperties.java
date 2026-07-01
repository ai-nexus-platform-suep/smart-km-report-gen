package com.powerreport.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.template")
public class TemplateStorageProperties {

    private String storageType = "MINIO";
    private String storageDir = "./storage/templates";
    private Minio minio = new Minio();

    @Data
    public static class Minio {

        private String endpoint = "http://127.0.0.1:9005";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "report-templates";
        private String objectPrefix = "templates";
        private boolean autoCreateBucket = true;
    }
}
