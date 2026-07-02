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
public class OutlineDraftRequest {

    /**
     * /outline/generate returned tempId. Used when outline is omitted.
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
     * Current frontend outline tree. Empty is allowed for an early draft.
     */
    @Valid
    private List<OutlineNodeResponse> outline = new ArrayList<>();
}
