package com.powerreport.admin.service;

import com.powerreport.admin.dto.TemplateConfigSchemaResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateVisualConfigDto;

public interface TemplateVisualEditorService {

    TemplateConfigSchemaResponse getConfigSchema();

    TemplateVisualConfigDto getDefaultConfig(String reportType);

    TemplateVisualConfigDto getVisualConfig(String templateId);

    TemplateResponse updateVisualConfig(String templateId, TemplateVisualConfigDto config);
}
