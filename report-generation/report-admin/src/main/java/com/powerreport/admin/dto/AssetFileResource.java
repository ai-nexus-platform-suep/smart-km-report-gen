package com.powerreport.admin.dto;

import org.springframework.core.io.Resource;

public record AssetFileResource(String fileName, String contentType, Resource resource, long contentLength) {
}
