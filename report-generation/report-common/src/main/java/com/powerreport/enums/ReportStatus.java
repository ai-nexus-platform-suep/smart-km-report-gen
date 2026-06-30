package com.powerreport.enums;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum ReportStatus {
    DRAFT("草稿"),
    OUTLINE_READY("大纲就绪"),
    CONTENT_GENERATING("正文生成中"),
    CONTENT_INCOMPLETE("正文待补全"),
    CONTENT_READY("正文就绪"),
    EXPORTED("已导出"),
    FAILED("生成失败");

    private final String label;

    ReportStatus(String label) {
        this.label = label;
    }

    public static ReportStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported report status: " + code));
    }
}
