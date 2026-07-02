package com.powerreport.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.asset")
public class AssetStorageProperties {

    private String storageType = "MINIO";
    private String storageDir = "./storage/assets";
    private Minio minio = new Minio();

    /**
     * 项目素材目录，用于一键导入 seed 数据。
     */
    private String seedDir = "./项目素材";

    @Data
    public static class Minio {

        private String endpoint = "http://127.0.0.1:9005";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "report-assets";
        private String objectPrefix = "assets";
        private boolean autoCreateBucket = true;
    }
}
