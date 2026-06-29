package com.powerreport.content;

import com.powerreport.config.ReportAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.powerreport")
@EnableConfigurationProperties(ReportAiProperties.class)
public class ReportContentFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportContentFlowApplication.class, args);
    }
}
