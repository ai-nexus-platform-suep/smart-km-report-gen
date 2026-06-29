package com.powerreport.content.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportHistoryPageResponse {

    private List<ReportHistoryItemResponse> records;
    private Long total;
    private Integer page;
    private Integer size;
}

