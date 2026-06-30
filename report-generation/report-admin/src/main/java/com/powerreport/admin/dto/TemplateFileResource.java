package com.powerreport.admin.dto;

import org.springframework.core.io.Resource;

public record TemplateFileResource(
        String fileName,
        Resource resource,
        long contentLength
) {
}
