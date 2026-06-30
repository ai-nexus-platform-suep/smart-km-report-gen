package com.powerreport.admin.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdminStatsDistributionResponse {

    private List<AdminDistributionItemResponse> reportType;
    private List<AdminDistributionItemResponse> reportStatus;
}
