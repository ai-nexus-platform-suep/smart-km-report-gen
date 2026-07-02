package com.powerreport.service;

import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineConfirmResponse;
import com.powerreport.dto.OutlineDraftRequest;
import com.powerreport.dto.OutlineDraftResponse;
import com.powerreport.dto.OutlineGenerateRequest;
import com.powerreport.dto.OutlineGenerateResponse;
import com.powerreport.dto.OutlineNodeResponse;
import com.powerreport.enums.ReportType;
import java.util.List;

public interface OutlineService {

    /**
     * Build report outline by report type.
     * Used as local fallback and for old compatibility endpoint.
     */
    List<OutlineNodeResponse> buildOutline(ReportType reportType);

    /**
     * Call AI outline service, parse result and store temporary outline state in Redis.
     */
    OutlineGenerateResponse generateOutline(OutlineGenerateRequest request);

    /**
     * Save confirmed outline into reports and report_outline_nodes.
     */
    OutlineConfirmResponse confirmOutline(OutlineConfirmRequest request);

    /**
     * Save a new draft report outline into reports and report_outline_nodes.
     */
    OutlineDraftResponse createDraftOutline(OutlineDraftRequest request);

    /**
     * Update an existing draft report outline.
     */
    OutlineDraftResponse updateDraftOutline(String reportId, OutlineDraftRequest request);

    /**
     * Read saved report metadata and outline tree.
     */
    OutlineDraftResponse getSavedOutline(String reportId);
}
