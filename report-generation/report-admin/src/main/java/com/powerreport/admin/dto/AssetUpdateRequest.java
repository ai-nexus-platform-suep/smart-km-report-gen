package com.powerreport.admin.dto;

import lombok.Data;

@Data
public class AssetUpdateRequest {

    private String name;
    private String category;
    private String description;
    private String tags;
    private Boolean enabled;
}
