package com.powerreport.admin.dto;

import lombok.Data;

@Data
public class AdminAlertResponse {

    private String type;
    private String level;
    private String message;
    private Long count;
}
