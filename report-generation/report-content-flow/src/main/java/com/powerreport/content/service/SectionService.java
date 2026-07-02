package com.powerreport.content.service;

import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SectionService {

    SectionGenerateResponse startGeneration(String reportId, SectionGenerateRequest request);

    SseEmitter streamSections(String reportId);

    SectionResponse saveSection(String reportId, String sectionId, SectionContentRequest request);

    SectionResponse getSection(String reportId, String sectionId);

    List<SectionResponse> listSections(String reportId);

    SectionGenerateResponse regenerateSection(String reportId, String sectionId, SectionRegenerateRequest request);
}

