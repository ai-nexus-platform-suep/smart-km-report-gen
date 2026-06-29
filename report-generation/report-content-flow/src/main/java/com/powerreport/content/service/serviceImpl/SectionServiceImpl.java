package com.powerreport.content.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.SectionService;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.enums.SectionStatus;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

    private static final String STATUS_GENERATING = "GENERATING";
    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_REGENERATED = "REGENERATED";
    private static final String SOURCE_USER_EDITED = "USER_EDITED";

    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportSectionMapper sectionMapper;

    @Override
    @Transactional
    public SectionGenerateResponse startGeneration(String reportId) {
        ReportEntity report = findReport(reportId);
        List<ReportSectionEntity> sections = ensureSectionRows(report);
        report.setStatus(STATUS_GENERATING);
        report.setTotalSections(sections.size());
        report.setCompletedSections(countCompleted(reportId));
        reportMapper.updateById(report);

        // TODO: Submit async generation task and call AI section stream endpoint.
        return new SectionGenerateResponse(
                UUID.randomUUID().toString(),
                reportId,
                null,
                STATUS_GENERATING,
                sections.size(),
                report.getCompletedSections(),
                "章节生成任务已创建，SSE 通道骨架已就绪"
        );
    }

    @Override
    public SseEmitter streamSections(String reportId) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> emitCurrentSectionSnapshot(reportId, emitter));
        return emitter;
    }

    @Override
    @Transactional
    public SectionResponse saveSection(String reportId, String sectionId, SectionContentRequest request) {
        ReportSectionEntity section = findSection(reportId, sectionId);
        section.setContentMarkdown(request.getContentMarkdown());
        section.setStatus(SectionStatus.USER_EDITED.name());
        section.setSource(SOURCE_USER_EDITED);
        section.setVersion(nextVersion(section));
        section.setErrorMessage(null);
        sectionMapper.updateById(section);
        updateCompletedSections(reportId);
        return toSectionResponse(section);
    }

    @Override
    public SectionResponse getSection(String reportId, String sectionId) {
        return toSectionResponse(findSection(reportId, sectionId));
    }

    @Override
    public List<SectionResponse> listSections(String reportId) {
        findReport(reportId);
        return sectionMapper.selectList(
                        new LambdaQueryWrapper<ReportSectionEntity>()
                                .eq(ReportSectionEntity::getReportId, reportId)
                                .orderByAsc(ReportSectionEntity::getNumber)
                )
                .stream()
                .map(this::toSectionResponse)
                .toList();
    }

    @Override
    @Transactional
    public SectionGenerateResponse regenerateSection(
            String reportId,
            String sectionId,
            SectionRegenerateRequest request
    ) {
        ReportSectionEntity section = findSection(reportId, sectionId);
        section.setStatus(SectionStatus.GENERATING.name());
        section.setSource(SOURCE_REGENERATED);
        section.setErrorMessage(null);
        sectionMapper.updateById(section);

        // TODO: Forward section context, existing content and request.hint to AI section stream,
        // then persist regenerated Markdown back into report_sections.content_markdown.
        return new SectionGenerateResponse(
                UUID.randomUUID().toString(),
                reportId,
                sectionId,
                SectionStatus.GENERATING.name(),
                null,
                null,
                "单章节重新生成任务已创建，等待接入 AI 流式内容"
        );
    }

    private void emitCurrentSectionSnapshot(String reportId, SseEmitter emitter) {
        try {
            List<SectionResponse> sections = listSections(reportId);
            int total = sections.size();
            for (int i = 0; i < total; i++) {
                SectionResponse section = sections.get(i);
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(Map.of(
                                "current", i + 1,
                                "total", total,
                                "sectionTitle", section.getTitle()
                        )));
                if (StringUtils.hasText(section.getContentMarkdown())) {
                    emitter.send(SseEmitter.event()
                            .name("content")
                            .data(section.getContentMarkdown()));
                }
                emitter.send(SseEmitter.event()
                        .name("section_done")
                        .data("[SECTION_DONE]"));
            }
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException | RuntimeException ex) {
            try {
                emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
            } catch (IOException ignored) {
                // Ignore secondary send failures while closing SSE connection.
            }
            emitter.completeWithError(ex);
        }
    }

    private ReportEntity findReport(String reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null || Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("报告不存在或已删除");
        }
        return report;
    }

    private ReportSectionEntity findSection(String reportId, String sectionId) {
        ReportSectionEntity section = sectionMapper.selectOne(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .eq(ReportSectionEntity::getId, sectionId)
        );
        if (section == null) {
            throw new IllegalArgumentException("章节不存在");
        }
        return section;
    }

    private List<ReportSectionEntity> ensureSectionRows(ReportEntity report) {
        List<ReportOutlineNodeEntity> outlineNodes = outlineNodeMapper.selectList(
                new LambdaQueryWrapper<ReportOutlineNodeEntity>()
                        .eq(ReportOutlineNodeEntity::getReportId, report.getId())
                        .orderByAsc(ReportOutlineNodeEntity::getLevel)
                        .orderByAsc(ReportOutlineNodeEntity::getSortOrder)
                        .orderByAsc(ReportOutlineNodeEntity::getNumber)
        );
        if (outlineNodes.isEmpty()) {
            throw new IllegalStateException("报告大纲为空，无法生成章节内容");
        }

        List<ReportSectionEntity> existing = sectionMapper.selectList(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, report.getId())
        );
        List<String> existingOutlineIds = existing.stream()
                .map(ReportSectionEntity::getOutlineNodeId)
                .filter(StringUtils::hasText)
                .toList();

        for (ReportOutlineNodeEntity node : outlineNodes) {
            if (existingOutlineIds.contains(node.getId())) {
                continue;
            }
            ReportSectionEntity section = new ReportSectionEntity();
            section.setId(UUID.randomUUID().toString());
            section.setReportId(report.getId());
            section.setOutlineNodeId(node.getId());
            section.setNumber(node.getNumber());
            section.setTitle(node.getTitle());
            section.setContentMarkdown(null);
            section.setStatus(SectionStatus.PENDING.name());
            section.setSource(SOURCE_AI);
            section.setVersion(1);
            sectionMapper.insert(section);
        }

        return sectionMapper.selectList(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, report.getId())
                        .orderByAsc(ReportSectionEntity::getNumber)
        );
    }

    private int countCompleted(String reportId) {
        return Math.toIntExact(sectionMapper.selectCount(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .in(ReportSectionEntity::getStatus,
                                SectionStatus.GENERATED.name(),
                                SectionStatus.USER_EDITED.name())
        ));
    }

    private void updateCompletedSections(String reportId) {
        ReportEntity report = findReport(reportId);
        report.setCompletedSections(countCompleted(reportId));
        reportMapper.updateById(report);
    }

    private int nextVersion(ReportSectionEntity section) {
        return section.getVersion() == null ? 1 : section.getVersion() + 1;
    }

    private SectionResponse toSectionResponse(ReportSectionEntity section) {
        SectionResponse response = new SectionResponse();
        response.setSectionId(section.getId());
        response.setOutlineNodeId(section.getOutlineNodeId());
        response.setReportId(section.getReportId());
        response.setNumber(section.getNumber());
        response.setTitle(section.getTitle());
        response.setContentMarkdown(section.getContentMarkdown());
        response.setStatus(section.getStatus());
        response.setSource(section.getSource());
        response.setVersion(section.getVersion());
        response.setErrorMessage(section.getErrorMessage());
        response.setUpdatedAt(section.getUpdatedAt());
        return response;
    }
}
