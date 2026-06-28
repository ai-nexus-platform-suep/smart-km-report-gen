package com.powerreport.dto;

import java.util.List;

public record OutlineConfirmResponse(
        String reportId,
        String status,
        int outlineCount,
        List<OutlineNodeResponse> outline
) {
}
