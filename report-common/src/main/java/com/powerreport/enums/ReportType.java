package com.powerreport.enums;

import lombok.Getter;

@Getter
public enum ReportType {
    SUMMER_PEAK_CHECK("迎峰度夏检查报告"),
    COAL_INVENTORY_AUDIT("煤库库存审计报告");

    private final String label;

    ReportType(String label) {
        this.label = label;
    }
}
