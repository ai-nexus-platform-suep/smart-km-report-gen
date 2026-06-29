package com.powerreport.content.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SectionContentRequest {

    /**
     * Frontend edited Markdown content. Empty string means clearing content.
     */
    @NotNull
    private String contentMarkdown;
}

