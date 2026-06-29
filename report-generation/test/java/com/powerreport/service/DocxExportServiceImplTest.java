package com.powerreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.powerreport.config.ReportExportProperties;
import com.powerreport.dto.ReportDocxExportRequest;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportFileEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.enums.CaptionNumberingMode;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportFileMapper;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import com.powerreport.service.serviceImpl.DocxExportServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocxExportServiceImplTest {

    @TempDir
    private Path tempDir;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportOutlineNodeMapper outlineNodeMapper;

    @Mock
    private ReportSectionMapper sectionMapper;

    @Mock
    private ReportFileMapper fileMapper;

    private DocxExportServiceImpl docxExportService;

    @BeforeEach
    void setUp() {
        ReportExportProperties properties = new ReportExportProperties();
        properties.setExportDir(tempDir.toString());
        docxExportService = new DocxExportServiceImpl(
                properties,
                reportMapper,
                outlineNodeMapper,
                sectionMapper,
                fileMapper
        );
    }

    @Test
    void exportReportWritesDocxAndPersistsFileRecord() throws Exception {
        when(reportMapper.selectById("report-1")).thenReturn(sampleReport());
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of(sampleOutlineNode()));
        when(sectionMapper.selectList(any())).thenReturn(List.of(sampleSection()));
        when(fileMapper.insert(any(ReportFileEntity.class))).thenReturn(1);

        ReportDocxExportRequest request = new ReportDocxExportRequest();
        request.setFigureNumberingMode(CaptionNumberingMode.GLOBAL);
        request.setTableNumberingMode(CaptionNumberingMode.SECTION);
        request.setIncludeEmptySections(true);

        var response = docxExportService.exportReport("report-1", request);

        assertThat(response.fileId()).isNotBlank();
        assertThat(response.reportId()).isEqualTo("report-1");
        assertThat(response.fileName()).endsWith(".docx");
        assertThat(response.fileSize()).isPositive();
        assertThat(response.sha256()).hasSize(64);
        assertThat(response.downloadUrl()).isEqualTo("/api/reports/files/" + response.fileId() + "/download");

        ArgumentCaptor<ReportFileEntity> fileCaptor = ArgumentCaptor.forClass(ReportFileEntity.class);
        org.mockito.Mockito.verify(fileMapper).insert(fileCaptor.capture());
        ReportFileEntity savedFile = fileCaptor.getValue();
        assertThat(savedFile.getReportId()).isEqualTo("report-1");
        assertThat(Files.exists(Path.of(savedFile.getFilePath()))).isTrue();
        assertThat(Files.size(Path.of(savedFile.getFilePath()))).isEqualTo(savedFile.getFileSize());
    }

    @Test
    void exportReportRejectsMissingReport() {
        when(reportMapper.selectById("missing-report")).thenReturn(null);

        assertThatThrownBy(() -> docxExportService.exportReport("missing-report", new ReportDocxExportRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("报告不存在或已删除");
    }

    @Test
    void getFileForDownloadRejectsMissingFileRecord() {
        when(fileMapper.selectById("missing-file")).thenReturn(null);

        assertThatThrownBy(() -> docxExportService.getFileForDownload("missing-file"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("导出文件不存在");
    }

    private ReportEntity sampleReport() {
        ReportEntity report = new ReportEntity();
        report.setId("report-1");
        report.setName("2026 年迎峰度夏专项检查报告");
        report.setReportType(ReportType.SUMMER_PEAK_CHECK.name());
        report.setSubject("2026 年迎峰度夏专项检查");
        report.setSpecialty("电气");
        report.setPowerPlant("示例电厂");
        report.setReportYear(2026);
        report.setStatus("DRAFT");
        report.setDeleted(false);
        return report;
    }

    private ReportOutlineNodeEntity sampleOutlineNode() {
        ReportOutlineNodeEntity node = new ReportOutlineNodeEntity();
        node.setId("outline-1");
        node.setReportId("report-1");
        node.setLevel(1);
        node.setSortOrder(1);
        node.setNumber("1");
        node.setTitle("检查概况");
        return node;
    }

    private ReportSectionEntity sampleSection() {
        ReportSectionEntity section = new ReportSectionEntity();
        section.setId("section-1");
        section.setReportId("report-1");
        section.setOutlineNodeId("outline-1");
        section.setNumber("1");
        section.setTitle("检查概况");
        section.setContentMarkdown("""
                本次检查覆盖主设备、辅机系统及防汛防高温措施。

                表：检查问题统计
                | 类型 | 数量 |
                | --- | --- |
                | 一般问题 | 3 |

                ![现场检查照片](local://image-1)
                """);
        return section;
    }
}
