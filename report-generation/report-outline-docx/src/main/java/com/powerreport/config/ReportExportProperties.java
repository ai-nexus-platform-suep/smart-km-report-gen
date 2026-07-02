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

    /**
     * Whether DOCX export should try to use the enabled template of the report type.
     */
    private boolean templateEnabled = true;

    /**
     * Local fallback directory for old LOCAL template records.
     */
    private String templateStorageDir = "./storage/templates";

    private boolean preferTemplateHeaderFooter = true;
    private boolean headerEnabled = true;
    private boolean footerEnabled = true;
    private boolean renderReportHeader = true;

    private String headerText = "{reportName}";
    private String footerText = "第 {page} 页 / 共 {pages} 页";

    private String bodyFont = "仿宋_GB2312";
    private String titleFont = "黑体";
    private int titleFontSize = 22;
    private int heading1FontSize = 16;
    private int heading2FontSize = 14;
    private int heading3FontSize = 12;
    private int bodyFontSize = 12;
    private int captionFontSize = 10;
    private int tableFontSize = 11;
    private double lineSpacing = 1.5;
    private int firstLineIndentTwips = 560;

    private long marginTopTwips = 1440;
    private long marginBottomTwips = 1440;
    private long marginLeftTwips = 1584;
    private long marginRightTwips = 1584;

    private Minio minio = new Minio();

    @Data
    public static class Minio {

        private String endpoint = "http://127.0.0.1:9005";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "report-templates";
    }
}
