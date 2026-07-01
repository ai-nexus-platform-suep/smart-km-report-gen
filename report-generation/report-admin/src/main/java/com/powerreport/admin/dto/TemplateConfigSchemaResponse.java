package com.powerreport.admin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TemplateConfigSchemaResponse {

    private String version = "1.0";
    private List<TemplateConfigFieldSchema> fields = new ArrayList<>();
    private TemplateVisualConfigDto defaultConfig;
}
