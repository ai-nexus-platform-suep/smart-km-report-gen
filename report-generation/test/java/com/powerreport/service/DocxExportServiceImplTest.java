package com.powerreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerreport.config.ReportExportProperties;
import com.powerreport.dto.ReportDocxExportRequest;
import com.powerreport.dto.ReportFileResponse;
import com.powerreport.dto.StoredReportFile;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportFileEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.enums.CaptionNumberingMode;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportFileMapper;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import com.powerreport.service.serviceImpl.DocxExportServiceImpl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocxExportServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportOutlineNodeMapper outlineNodeMapper;

    @Mock
    private ReportSectionMapper sectionMapper;

    @Mock
    private ReportFileMapper fileMapper;

    @TempDir
    private Path tempDir;

    private DocxExportServiceImpl service;

    @BeforeEach
    void setUp() {
        ReportExportProperties properties = new ReportExportProperties();
        properties.setExportDir(tempDir.toString());
        service = new DocxExportServiceImpl(properties, reportMapper, outlineNodeMapper, sectionMapper, fileMapper);
    }

    @Test
    void exportRendersMarkdownHeadingsTablesAndImageCaptions() throws Exception {
        when(reportMapper.selectById("report-1")).thenReturn(report("report-1"));
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of(node("node-1", null, "1", "Main", 1, 1)));
        when(sectionMapper.selectList(any())).thenReturn(List.of(section("section-1", "node-1", "1",
                """
                ## Risk Review
                Table: Equipment Status
                | Name | Result |
                | --- | --- |
                | Transformer | Normal |
                ![Figure Caption](figure.png)
                """)));

        ReportDocxExportRequest request = new ReportDocxExportRequest();
        request.setFigureNumberingMode(CaptionNumberingMode.SECTION);
        request.setTableNumberingMode(CaptionNumberingMode.SECTION);
        service.exportReport("report-1", request);

        ReportFileEntity file = insertedFile();
        try (XWPFDocument document = open(file)) {
            List<String> paragraphs = paragraphText(document);
            assertThat(paragraphs).anyMatch(text -> text.contains("Risk Review"));
            assertThat(paragraphs).anyMatch(text -> text.contains("Equipment Status"));
            assertThat(paragraphs).anyMatch(text -> text.contains("Figure Caption"));
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("Name");
            assertThat(document.getTables().get(0).getRow(1).getCell(1).getText()).isEqualTo("Normal");
        }
    }

    @Test
    void exportSkipsEmptyOutlineSectionsWhenDisabled() throws Exception {
        when(reportMapper.selectById("report-1")).thenReturn(report("report-1"));
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of(
                node("node-1", null, "1", "Filled", 1, 1),
                node("node-2", null, "2", "Empty", 1, 2)
        ));
        when(sectionMapper.selectList(any())).thenReturn(List.of(
                section("section-1", "node-1", "1", "body"),
                section("section-2", "node-2", "2", "")
        ));

        ReportDocxExportRequest request = new ReportDocxExportRequest();
        request.setIncludeEmptySections(false);
        service.exportReport("report-1", request);

        try (XWPFDocument document = open(insertedFile())) {
            List<String> paragraphs = paragraphText(document);
            assertThat(paragraphs).anyMatch(text -> text.contains("1 Filled"));
            assertThat(paragraphs).noneMatch(text -> text.contains("2 Empty"));
        }
    }

    @Test
    void exportSortsSectionsByNumberWhenOutlineIsMissing() throws Exception {
        when(reportMapper.selectById("report-1")).thenReturn(report("report-1"));
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of());
        when(sectionMapper.selectList(any())).thenReturn(List.of(
                section("section-10", null, "10", "ten"),
                section("section-2", null, "2", "two")
        ));

        service.exportReport("report-1", new ReportDocxExportRequest());

        try (XWPFDocument document = open(insertedFile())) {
            String text = String.join("\n", paragraphText(document));
            assertThat(text.indexOf("2 Section 2")).isLessThan(text.indexOf("10 Section 10"));
        }
    }

    @Test
    void exportPersistsFileRecordAndMarksReportExported() throws Exception {
        ReportEntity report = report("report-1");
        when(reportMapper.selectById("report-1")).thenReturn(report);
        when(outlineNodeMapper.selectList(any())).thenReturn(List.of());
        when(sectionMapper.selectList(any())).thenReturn(List.of(section("section-1", null, "1", "body")));

        ReportFileResponse response = service.exportReport("report-1", new ReportDocxExportRequest());

        ReportFileEntity file = insertedFile();
        assertThat(response.reportId()).isEqualTo("report-1");
        assertThat(response.fileName()).endsWith(".docx");
        assertThat(file.getFileSize()).isGreaterThan(0);
        assertThat(file.getSha256()).hasSize(64);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.EXPORTED.name());
        verify(reportMapper).updateById(report);
    }

    @Test
    void getFileForDownloadRejectsMissingPhysicalFile() {
        ReportFileEntity file = new ReportFileEntity();
        file.setId("file-1");
        file.setFileName("missing.docx");
        file.setFilePath(tempDir.resolve("missing.docx").toString());
        file.setFileSize(0L);
        when(fileMapper.selectById("file-1")).thenReturn(file);

        assertThatThrownBy(() -> service.getFileForDownload("file-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFileForDownloadUsesPhysicalSizeWhenStoredSizeIsMissing() throws IOException {
        Path path = tempDir.resolve("stored.docx");
        Files.writeString(path, "docx", StandardCharsets.UTF_8);
        ReportFileEntity file = new ReportFileEntity();
        file.setId("file-1");
        file.setFileName("stored.docx");
        file.setFilePath(path.toString());
        file.setFileSize(null);
        when(fileMapper.selectById("file-1")).thenReturn(file);

        StoredReportFile stored = service.getFileForDownload("file-1");

        assertThat(stored.fileName()).isEqualTo("stored.docx");
        assertThat(stored.fileSize()).isEqualTo(Files.size(path));
    }

    private ReportFileEntity insertedFile() {
        ArgumentCaptor<ReportFileEntity> captor = ArgumentCaptor.forClass(ReportFileEntity.class);
        verify(fileMapper).insert(captor.capture());
        return captor.getValue();
    }

    private XWPFDocument open(ReportFileEntity file) throws IOException {
        return new XWPFDocument(Files.newInputStream(Path.of(file.getFilePath())));
    }

    private List<String> paragraphText(XWPFDocument document) {
        return document.getParagraphs().stream()
                .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText())
                .toList();
    }

    private ReportEntity report(String id) {
        ReportEntity report = new ReportEntity();
        report.setId(id);
        report.setName("Safety Report");
        report.setReportType(ReportType.SUMMER_PEAK_CHECK.name());
        report.setPowerPlant("Plant A");
        report.setSpecialty("Electrical");
        report.setReportYear(2026);
        report.setSubject("Summer safety");
        report.setDeleted(false);
        return report;
    }

    private ReportOutlineNodeEntity node(String id, String parentId, String number, String title, int level, int sortOrder) {
        ReportOutlineNodeEntity node = new ReportOutlineNodeEntity();
        node.setId(id);
        node.setReportId("report-1");
        node.setParentId(parentId);
        node.setNumber(number);
        node.setTitle(title);
        node.setLevel(level);
        node.setSortOrder(sortOrder);
        return node;
    }

    private ReportSectionEntity section(String id, String outlineNodeId, String number, String content) {
        ReportSectionEntity section = new ReportSectionEntity();
        section.setId(id);
        section.setReportId("report-1");
        section.setOutlineNodeId(outlineNodeId);
        section.setNumber(number);
        section.setTitle("Section " + number);
        section.setContentMarkdown(content);
        return section;
    }
}
