package com.km.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParserConfigVO {

    private String backend;
    private Integer maxConcurrency;
    private LocalDateTime updatedAt;
}
