package com.powerreport.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OutlineDraftResponse {

    private String reportId;
    private String status;
    private String name;
    private String reportType;
    private String subject;
    private String specialty;
    private String powerPlant;
    private Integer reportYear;
    private int outlineCount;
    private List<OutlineNodeResponse> outline = new ArrayList<>();
}
