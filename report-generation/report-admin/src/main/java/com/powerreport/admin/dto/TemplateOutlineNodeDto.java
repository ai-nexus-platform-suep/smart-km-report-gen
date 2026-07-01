package com.powerreport.admin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TemplateOutlineNodeDto {

    private String id;
    private String number;
    private String title;
    private Integer level;
    private String promptHint;
    private List<TemplateOutlineNodeDto> children = new ArrayList<>();
}
