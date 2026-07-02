package com.powerreport.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OutlineNodeResponse {

    private String id;
    private String number;
    private String title;
    private Integer level;
    private String promptHint;
    private List<OutlineTablePlan> tables = new ArrayList<>();
    private List<OutlineNodeResponse> children = new ArrayList<>();
}
