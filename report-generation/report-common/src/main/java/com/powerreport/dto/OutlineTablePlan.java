package com.powerreport.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OutlineTablePlan {

    /**
     * Frontend stable key for the table plan. Optional when creating an outline.
     */
    private String id;

    /**
     * Table caption without auto numbering, for example "设备检查情况".
     */
    private String caption;

    /**
     * Markdown table columns determined at outline stage.
     */
    private List<String> columns = new ArrayList<>();

    /**
     * Optional note for AI/template generation.
     */
    private String description;
}
