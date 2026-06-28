package com.powerreport.service;

import com.powerreport.dto.ReportDocxExportRequest;
import com.powerreport.dto.ReportFileResponse;
import com.powerreport.dto.StoredReportFile;
import java.io.IOException;

public interface DocxExportService {

    /**
     * Static rebuild export: read saved report draft from DB and generate DOCX without calling LLM.
     */
    ReportFileResponse exportReport(String reportId, ReportDocxExportRequest request) throws IOException;

    /**
     * Read generated DOCX file metadata for download.
     */
    StoredReportFile getFileForDownload(String fileId) throws IOException;
}
