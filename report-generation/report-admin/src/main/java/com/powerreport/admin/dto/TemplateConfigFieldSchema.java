package com.powerreport.admin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TemplateConfigFieldSchema {

    private String key;
    private String label;
    private String type;
    private String description;
    private Object defaultValue;
    private List<String> options = new ArrayList<>();
}
