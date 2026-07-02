package com.powerreport.dto;

import com.powerreport.enums.ReportType;
import com.powerreport.enums.ContentGenerationMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class OutlineGenerateRequest {

    @NotNull
    private ReportType reportType;

    /**
     * AI: call AI outline service. TEMPLATE: read enabled template config_json.outline.
     */
    private ContentGenerationMode generationMode = ContentGenerationMode.AI;

    /**
     * Optional report_templates.id. Used when generationMode=TEMPLATE or as template preference.
     */
    private String templateId;

    @NotBlank
    private String subject;

    private String name;
    private String specialty;
    private String powerPlant;

    @Min(2000)
    private Integer reportYear;

    /**
     * 预留给前端或网关传递额外上下文，如机组、地区、素材范围等。
     */
    private Map<String, Object> context = new LinkedHashMap<>();
}
