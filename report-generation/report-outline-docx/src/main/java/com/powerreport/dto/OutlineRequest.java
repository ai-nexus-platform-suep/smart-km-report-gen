package com.powerreport.dto;

import com.powerreport.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OutlineRequest {

    @NotNull
    private ReportType reportType;
}
