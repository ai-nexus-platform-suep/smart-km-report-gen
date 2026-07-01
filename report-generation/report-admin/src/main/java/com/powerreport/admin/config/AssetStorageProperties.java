package com.powerreport.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.asset")
public class AssetStorageProperties {

    private String storageDir = "./storage/assets";

    /**
     * 项目素材目录，用于一键导入 seed 数据。
     */
    private String seedDir = "./项目素材";
}
