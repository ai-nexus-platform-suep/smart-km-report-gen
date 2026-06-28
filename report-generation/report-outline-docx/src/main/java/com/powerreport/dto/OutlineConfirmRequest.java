package com.powerreport.dto;

import com.powerreport.enums.ReportType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class OutlineConfirmRequest {

    /**
     * /outline/generate 返回的临时 ID。前端传最终编辑后的 outline 时可选。
     */
    private String tempId;

    private String name;

    @NotNull
    private ReportType reportType;

    @NotBlank
    private String subject;

    @NotBlank
    private String specialty;

    @NotBlank
    private String powerPlant;

    @NotNull
    @Min(2000)
    private Integer reportYear;

    /**
     * 用户最终确认的大纲 JSON；若为空，则后端尝试按 tempId 从 Redis 读取。
     */
    @Valid
    private List<OutlineNodeResponse> outline = new ArrayList<>();
}
