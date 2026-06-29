package com.powerreport;

import com.powerreport.config.ReportAiProperties;
import com.powerreport.config.ReportExportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        ReportExportProperties.class,
        ReportAiProperties.class
})
public class ReportOutlineDocxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportOutlineDocxApplication.class, args);
    }
}
