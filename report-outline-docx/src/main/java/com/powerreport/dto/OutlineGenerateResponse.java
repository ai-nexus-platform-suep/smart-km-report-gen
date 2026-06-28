package com.powerreport.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OutlineGenerateResponse {

    private String tempId;
    private String source;
    private long expireSeconds;
    private List<OutlineNodeResponse> outline = new ArrayList<>();
}
