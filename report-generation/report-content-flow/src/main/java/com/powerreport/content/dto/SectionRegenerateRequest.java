package com.powerreport.content.dto;

import lombok.Data;

@Data
public class SectionRegenerateRequest {

    /**
     * User hint, such as "请补充防汛物资储备数量".
     */
    private String hint;
}

