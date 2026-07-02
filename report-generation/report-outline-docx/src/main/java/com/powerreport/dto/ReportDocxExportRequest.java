package com.powerreport.dto;

import com.powerreport.enums.CaptionNumberingMode;
import lombok.Data;

@Data
public class ReportDocxExportRequest {

    /**
     * Optional report_templates.id. Empty means using the latest enabled template of the report type.
     */
    private String templateId;

    /**
     * Whether to apply report template and template config when exporting.
     */
    private Boolean useTemplate = true;

    /**
     * GLOBAL: 图 1；SECTION: 图 1.1。
     */
    private CaptionNumberingMode figureNumberingMode = CaptionNumberingMode.GLOBAL;

    /**
     * GLOBAL: 表 1；SECTION: 表 1.1。
     */
    private CaptionNumberingMode tableNumberingMode = CaptionNumberingMode.GLOBAL;

    /**
     * true 时即使章节正文为空，也会按大纲输出标题，方便导出草稿。
     */
    private Boolean includeEmptySections = true;
}
