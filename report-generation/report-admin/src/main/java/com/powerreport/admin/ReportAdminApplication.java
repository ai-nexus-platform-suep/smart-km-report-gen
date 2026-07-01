package com.powerreport.admin;

import com.powerreport.admin.config.AssetStorageProperties;
import com.powerreport.admin.config.TemplateStorageProperties;
import com.powerreport.config.ReportAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.powerreport")
@EnableConfigurationProperties({ReportAiProperties.class, TemplateStorageProperties.class, AssetStorageProperties.class})
public class ReportAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportAdminApplication.class, args);
    }
}
