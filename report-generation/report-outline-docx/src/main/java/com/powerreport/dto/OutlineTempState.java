package com.powerreport.dto;

import com.powerreport.enums.ReportType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OutlineTempState {

    private String tempId;
    private String name;
    private ReportType reportType;
    private String subject;
    private String specialty;
    private String powerPlant;
    private Integer reportYear;
    private String source;
    private LocalDateTime generatedAt;
    private List<OutlineNodeResponse> outline = new ArrayList<>();
}
