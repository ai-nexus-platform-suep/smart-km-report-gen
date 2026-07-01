package com.powerreport.admin.dto;

import java.util.List;
import lombok.Data;

@Data
public class TemplatePageResponse {

    private Long total;
    private Integer page;
    private Integer size;
    private List<TemplateResponse> records;
}
