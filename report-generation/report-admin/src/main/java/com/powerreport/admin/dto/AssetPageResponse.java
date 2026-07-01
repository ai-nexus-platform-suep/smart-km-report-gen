package com.powerreport.admin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AssetPageResponse {

    private Long total;
    private Integer page;
    private Integer size;
    private List<AssetResponse> records = new ArrayList<>();
}
