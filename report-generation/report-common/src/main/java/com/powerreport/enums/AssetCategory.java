package com.powerreport.enums;

import lombok.Getter;

@Getter
public enum AssetCategory {
    STANDARD_DOC("标准文档"),
    REPORT_DATA("报告数据"),
    OTHER("其他");

    private final String label;

    AssetCategory(String label) {
        this.label = label;
    }
}
