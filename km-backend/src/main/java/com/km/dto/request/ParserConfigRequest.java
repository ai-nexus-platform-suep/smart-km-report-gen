package com.km.dto.request;

import lombok.Data;

@Data
public class ParserConfigRequest {

    private String backend;
    private Integer maxConcurrency;
}
