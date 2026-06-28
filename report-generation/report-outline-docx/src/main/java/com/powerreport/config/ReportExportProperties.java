package com.powerreport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.report")
public class ReportExportProperties {

    /**
     * Local directory for exported DOCX files.
     * Current project stores exported files on local disk.
     */
    private String exportDir = "./storage/reports";
}
