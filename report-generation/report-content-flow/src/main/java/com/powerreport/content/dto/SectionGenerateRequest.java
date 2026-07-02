package com.powerreport.content.dto;

import com.powerreport.enums.ContentGenerationMode;
import lombok.Data;

@Data
public class SectionGenerateRequest {

    /**
     * AI: call ai-service. TEMPLATE: create editable content from confirmed outline and table plans.
     */
    private ContentGenerationMode generationMode = ContentGenerationMode.AI;
}
