package com.powerreport.dto;

import java.nio.file.Path;

public record StoredReportFile(
        String fileId,
        String fileName,
        Path path,
        long fileSize
) {
}
