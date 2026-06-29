package com.powerreport.content.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerreport.content.dto.OutlineNodeResponse;
import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryItemResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.HistoryService;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportSectionMapper sectionMapper;

    @Override
    public ReportHistoryPageResponse listReports(Integer page, Integer size) {
        int current = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);

        Page<ReportEntity> result = reportMapper.selectPage(
                Page.of(current, pageSize),
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(ReportEntity::getDeleted, false)
                        .orderByDesc(ReportEntity::getCreatedAt)
        );

        List<ReportHistoryItemResponse> records = result.getRecords()
                .stream()
                .map(this::toHistoryItem)
                .toList();
        return new ReportHistoryPageResponse(records, result.getTotal(), current, pageSize);
    }

    @Override
    public ReportHistoryDetailResponse getReportDetail(String reportId) {
        ReportEntity report = findReport(reportId);
        ReportHistoryDetailResponse response = new ReportHistoryDetailResponse();
        response.setReportId(report.getId());
        response.setName(report.getName());
        response.setType(report.getReportType());
        response.setSubject(report.getSubject());
        response.setSpecialty(report.getSpecialty());
        response.setPowerPlant(report.getPowerPlant());
        response.setReportYear(report.getReportYear());
        response.setStatus(report.getStatus());
        response.setTotalSections(report.getTotalSections());
        response.setCompletedSections(report.getCompletedSections());
        response.setCreatedAt(report.getCreatedAt());
        response.setUpdatedAt(report.getUpdatedAt());
        response.setOutline(buildOutlineTree(reportId));
        response.setSections(listSectionResponses(reportId));
        return response;
    }

    @Override
    public void deleteReport(String reportId) {
        ReportEntity report = findReport(reportId);
        report.setDeleted(true);
        reportMapper.updateById(report);
    }

    private ReportEntity findReport(String reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null || Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("报告不存在或已删除");
        }
        return report;
    }

    private ReportHistoryItemResponse toHistoryItem(ReportEntity report) {
        ReportHistoryItemResponse item = new ReportHistoryItemResponse();
        item.setReportId(report.getId());
        item.setName(report.getName());
        item.setType(report.getReportType());
        item.setSubject(report.getSubject());
        item.setSpecialty(report.getSpecialty());
        item.setPowerPlant(report.getPowerPlant());
        item.setReportYear(report.getReportYear());
        item.setStatus(report.getStatus());
        item.setTotalSections(report.getTotalSections());
        item.setCompletedSections(report.getCompletedSections());
        item.setCreatedAt(report.getCreatedAt());
        item.setUpdatedAt(report.getUpdatedAt());
        return item;
    }

    private List<OutlineNodeResponse> buildOutlineTree(String reportId) {
        List<ReportOutlineNodeEntity> nodes = outlineNodeMapper.selectList(
                new LambdaQueryWrapper<ReportOutlineNodeEntity>()
                        .eq(ReportOutlineNodeEntity::getReportId, reportId)
                        .orderByAsc(ReportOutlineNodeEntity::getLevel)
                        .orderByAsc(ReportOutlineNodeEntity::getSortOrder)
                        .orderByAsc(ReportOutlineNodeEntity::getNumber)
        );

        Map<String, OutlineNodeResponse> byId = new LinkedHashMap<>();
        List<OutlineNodeResponse> roots = new ArrayList<>();
        for (ReportOutlineNodeEntity node : nodes) {
            OutlineNodeResponse response = toOutlineNode(node);
            byId.put(node.getId(), response);
            if (node.getParentId() == null) {
                roots.add(response);
                continue;
            }
            OutlineNodeResponse parent = byId.get(node.getParentId());
            if (parent == null) {
                roots.add(response);
            } else {
                parent.getChildren().add(response);
            }
        }
        return roots;
    }

    private OutlineNodeResponse toOutlineNode(ReportOutlineNodeEntity node) {
        OutlineNodeResponse response = new OutlineNodeResponse();
        response.setId(node.getId());
        response.setNumber(node.getNumber());
        response.setTitle(node.getTitle());
        response.setLevel(node.getLevel());
        response.setPromptHint(node.getPromptHint());
        return response;
    }

    private List<SectionResponse> listSectionResponses(String reportId) {
        return sectionMapper.selectList(
                        new LambdaQueryWrapper<ReportSectionEntity>()
                                .eq(ReportSectionEntity::getReportId, reportId)
                                .orderByAsc(ReportSectionEntity::getNumber)
                )
                .stream()
                .map(this::toSectionResponse)
                .toList();
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
