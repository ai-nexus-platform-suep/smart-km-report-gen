package com.powerreport.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerreport.content.dto.ReportHistoryDetailResponse;
import com.powerreport.content.dto.ReportHistoryPageResponse;
import com.powerreport.content.dto.ReportHistoryQueryRequest;
import com.powerreport.content.service.serviceImpl.HistoryServiceImpl;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.enums.SectionStatus;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportOutlineNodeMapper outlineNodeMapper;

    @Mock
    private ReportSectionMapper sectionMapper;

    private HistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HistoryServiceImpl(reportMapper, outlineNodeMapper, sectionMapper);
    }

    @Test
    void listReportsNormalizesPagingAndMapsRecords() {
        ReportEntity report = report("report-1");
        when(reportMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ReportEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(report));
            return page;
        });

        ReportHistoryQueryRequest query = new ReportHistoryQueryRequest();
        query.setPage(0);
        query.setSize(500);
        query.setStatus("exported");
        query.setType(ReportType.SUMMER_PEAK_CHECK.name());

        ReportHistoryPageResponse response = service.listReports(query);

        ArgumentCaptor<Page<ReportEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(reportMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getReportId()).isEqualTo("report-1");
    }

    @Test
    void getReportDetailBuildsOutlineTreeAndSections() {
        when(reportMapper.selectById("report-1")).thenReturn(report("report-1"));
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of(
                node("node-1", null, "1", "Overview", 1),
                node("node-2", "node-1", "1.1", "Background", 2)
        ));
        when(sectionMapper.selectList(any())).thenReturn(List.of(section("section-1", "node-2")));

        ReportHistoryDetailResponse detail = service.getReportDetail("report-1");

        assertThat(detail.getReportId()).isEqualTo("report-1");
        assertThat(detail.getOutline()).hasSize(1);
        assertThat(detail.getOutline().get(0).getChildren()).hasSize(1);
        assertThat(detail.getOutline().get(0).getChildren().get(0).getTitle()).isEqualTo("Background");
        assertThat(detail.getSections()).hasSize(1);
        assertThat(detail.getSections().get(0).getSectionId()).isEqualTo("section-1");
    }

    @Test
    void deleteReportMarksReportDeleted() {
        ReportEntity report = report("report-1");
        when(reportMapper.selectById("report-1")).thenReturn(report);

        service.deleteReport("report-1");

        assertThat(report.getDeleted()).isTrue();
        verify(reportMapper).updateById(report);
    }

    @Test
    void getReportDetailRejectsDeletedReport() {
        ReportEntity deleted = report("report-1");
        deleted.setDeleted(true);
        when(reportMapper.selectById("report-1")).thenReturn(deleted);

        assertThatThrownBy(() -> service.getReportDetail("report-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ReportEntity report(String id) {
        ReportEntity report = new ReportEntity();
        report.setId(id);
        report.setName("Report");
        report.setReportType(ReportType.SUMMER_PEAK_CHECK.name());
        report.setSubject("Safety");
        report.setSpecialty("Electrical");
        report.setPowerPlant("Plant A");
        report.setReportYear(2026);
        report.setStatus(ReportStatus.EXPORTED.name());
        report.setTotalSections(1);
        report.setCompletedSections(1);
        report.setDeleted(false);
        report.setCreatedAt(LocalDateTime.now().minusDays(1));
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }

    private ReportOutlineNodeEntity node(String id, String parentId, String number, String title, Integer level) {
        ReportOutlineNodeEntity node = new ReportOutlineNodeEntity();
        node.setId(id);
        node.setReportId("report-1");
        node.setParentId(parentId);
        node.setNumber(number);
        node.setTitle(title);
        node.setLevel(level);
        return node;
    }

    private ReportSectionEntity section(String id, String outlineNodeId) {
        ReportSectionEntity section = new ReportSectionEntity();
        section.setId(id);
        section.setReportId("report-1");
        section.setOutlineNodeId(outlineNodeId);
        section.setNumber("1.1");
        section.setTitle("Background");
        section.setContentMarkdown("content");
        section.setStatus(SectionStatus.GENERATED.name());
        section.setSource("AI");
        section.setVersion(1);
        return section;
    }
}
