package com.powerreport.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.config.ReportAiProperties;
import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.serviceImpl.SectionServiceImpl;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.enums.SectionStatus;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportOutlineNodeMapper outlineNodeMapper;

    @Mock
    private ReportSectionMapper sectionMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SectionServiceImpl(
                reportMapper,
                outlineNodeMapper,
                sectionMapper,
                new ReportAiProperties(),
                new ObjectMapper(),
                redisTemplate
        );
    }

    @Test
    void startGenerationCreatesMissingRowsAndMarksPendingSectionsGenerating() {
        ReportEntity report = report("report-1");
        List<ReportOutlineNodeEntity> outline = List.of(node("node-1", "1", "Overview"), node("node-2", "2", "Result"));
        List<ReportSectionEntity> sections = List.of(
                section("section-1", "node-1", "1", null, SectionStatus.PENDING, 1),
                section("section-2", "node-2", "2", "old", SectionStatus.GENERATED, 1)
        );
        when(reportMapper.selectById("report-1")).thenReturn(report);
        when(outlineNodeMapper.selectList(any())).thenReturn(outline);
        when(sectionMapper.selectList(any())).thenReturn(List.of()).thenReturn(sections);
        when(sectionMapper.selectCount(any())).thenReturn(1L);

        SectionGenerateResponse response = service.startGeneration("report-1");

        verify(sectionMapper, times(2)).insert(any(ReportSectionEntity.class));
        assertThat(sections.get(0).getStatus()).isEqualTo(SectionStatus.GENERATING.name());
        assertThat(sections.get(1).getStatus()).isEqualTo(SectionStatus.GENERATED.name());
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONTENT_GENERATING.name());
        assertThat(report.getTotalSections()).isEqualTo(2);
        assertThat(report.getCompletedSections()).isEqualTo(1);
        assertThat(response.getReportId()).isEqualTo("report-1");
        assertThat(response.getTotalSections()).isEqualTo(2);
        assertThat(response.getCompletedSections()).isEqualTo(1);
    }

    @Test
    void startGenerationRejectsReportWithoutOutline() {
        when(reportMapper.selectById("report-1")).thenReturn(report("report-1"));
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.startGeneration("report-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outline is empty");
    }

    @Test
    void saveSectionUpdatesVersionAndRefreshesReportProgress() {
        ReportSectionEntity section = section("section-1", "node-1", "1", "old", SectionStatus.GENERATED, 2);
        ReportEntity report = report("report-1");
        when(sectionMapper.selectOne(any())).thenReturn(section);
        when(reportMapper.selectById("report-1")).thenReturn(report);
        when(sectionMapper.selectCount(any())).thenReturn(1L, 1L, 0L, 0L);

        SectionContentRequest request = new SectionContentRequest();
        request.setContentMarkdown("edited");
        SectionResponse response = service.saveSection("report-1", "section-1", request);

        assertThat(section.getContentMarkdown()).isEqualTo("edited");
        assertThat(section.getStatus()).isEqualTo(SectionStatus.USER_EDITED.name());
        assertThat(section.getSource()).isEqualTo("USER_EDITED");
        assertThat(section.getVersion()).isEqualTo(3);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONTENT_READY.name());
        assertThat(response.getContentMarkdown()).isEqualTo("edited");
        verify(sectionMapper).updateById(section);
        verify(reportMapper).updateById(report);
    }

    @Test
    void regenerateSectionStoresHintAndMarksSectionGenerating() {
        ReportSectionEntity section = section("section-1", "node-1", "1", "old", SectionStatus.GENERATED, 1);
        ReportEntity report = report("report-1");
        when(sectionMapper.selectOne(any())).thenReturn(section);
        when(reportMapper.selectById("report-1")).thenReturn(report);
        when(sectionMapper.selectCount(any())).thenReturn(0L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SectionRegenerateRequest request = new SectionRegenerateRequest();
        request.setHint("focus on risks");
        SectionGenerateResponse response = service.regenerateSection("report-1", "section-1", request);

        assertThat(section.getStatus()).isEqualTo(SectionStatus.GENERATING.name());
        assertThat(section.getSource()).isEqualTo("REGENERATED");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CONTENT_GENERATING.name());
        assertThat(response.getSectionId()).isEqualTo("section-1");
        verify(valueOperations).set(eq("report:section:regenerate:report-1:section-1"), eq("focus on risks"), any());
    }

    @Test
    void listSectionsRejectsDeletedReports() {
        ReportEntity deleted = report("report-1");
        deleted.setDeleted(true);
        when(reportMapper.selectById("report-1")).thenReturn(deleted);

        assertThatThrownBy(() -> service.listSections("report-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deleted");
    }

    private ReportEntity report(String id) {
        ReportEntity report = new ReportEntity();
        report.setId(id);
        report.setName("Report");
        report.setReportType(ReportType.SUMMER_PEAK_CHECK.name());
        report.setSubject("Safety");
        report.setPowerPlant("Plant A");
        report.setSpecialty("Electrical");
        report.setReportYear(2026);
        report.setDeleted(false);
        return report;
    }

    private ReportOutlineNodeEntity node(String id, String number, String title) {
        ReportOutlineNodeEntity node = new ReportOutlineNodeEntity();
        node.setId(id);
        node.setReportId("report-1");
        node.setNumber(number);
        node.setTitle(title);
        node.setLevel(1);
        return node;
    }

    private ReportSectionEntity section(String id, String outlineNodeId, String number, String content,
                                        SectionStatus status, int version) {
        ReportSectionEntity section = new ReportSectionEntity();
        section.setId(id);
        section.setReportId("report-1");
        section.setOutlineNodeId(outlineNodeId);
        section.setNumber(number);
        section.setTitle("Section " + number);
        section.setContentMarkdown(content);
        section.setStatus(status.name());
        section.setSource("AI");
        section.setVersion(version);
        return section;
    }
}
