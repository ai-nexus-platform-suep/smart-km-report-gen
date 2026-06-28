package com.powerreport.dto;

public record ReportFileResponse(
        String fileId,
        String reportId,
        String fileName,
        long fileSize,
        String sha256,
        String downloadUrl
) {
}
