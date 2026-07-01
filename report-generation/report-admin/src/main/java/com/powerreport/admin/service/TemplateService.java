package com.powerreport.admin.service;

import com.powerreport.admin.dto.TemplateConfigRequest;
import com.powerreport.admin.dto.TemplateFileResource;
import com.powerreport.admin.dto.TemplatePageResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

public interface TemplateService {

    TemplatePageResponse list(Integer page, Integer size, String reportType, Boolean enabled, String keyword);

    TemplateResponse upload(MultipartFile file, String name, String reportType, String version,
                            String configJson, Boolean enabled, String username);

    TemplateResponse detail(String templateId);

    TemplateResponse update(String templateId, TemplateUpdateRequest request);

    TemplateResponse replaceFile(String templateId, MultipartFile file);

    String getConfig(String templateId);

    TemplateResponse updateConfig(String templateId, TemplateConfigRequest request);

    TemplateFileResource loadFile(String templateId);

    void delete(String templateId);
}
