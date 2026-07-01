package com.powerreport.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
 
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.admin.config.TemplateStorageProperties;
import com.powerreport.admin.dto.TemplateConfigRequest;
import com.powerreport.admin.dto.TemplateFileResource;
import com.powerreport.admin.dto.TemplatePageResponse;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateUpdateRequest;
import com.powerreport.admin.service.serviceImpl.TemplateServiceImpl;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportTemplateMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock
    private ReportTemplateMapper templateMapper;

    @TempDir
    private Path tempDir;

    private TemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        TemplateStorageProperties properties = new TemplateStorageProperties();
        properties.setStorageDir(tempDir.toString());
        service = new TemplateServiceImpl(templateMapper, properties, new ObjectMapper());
    }

    @Test
    void listNormalizesPagingAndMapsRecords() {
        ReportTemplateEntity entity = template("tpl-1", "Summer Peak", "1.0.0", "template.docx");
        when(templateMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ReportTemplateEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(entity));
            return page;
        });

        TemplatePageResponse response = service.list(0, 500, ReportType.SUMMER_PEAK_CHECK.name(), true, "peak");

        ArgumentCaptor<Page<ReportTemplateEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(templateMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(100);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getRecords().get(0).getName()).isEqualTo("Summer Peak");
    }

    @Test
    void uploadStoresDocxAndPersistsMetadata() {
        AtomicReference<ReportTemplateEntity> inserted = new AtomicReference<>();
        when(templateMapper.insert(any(ReportTemplateEntity.class))).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });

        TemplateResponse response = service.upload(
                docx("template.docx", "template-body"),
                "  Summer Peak  ",
                ReportType.SUMMER_PEAK_CHECK.name(),
                "",
                "{\"sections\":[]}",
                null,
                "admin"
        );

        assertThat(response.getId()).isNotBlank();
        assertThat(response.getName()).isEqualTo("Summer Peak");
        assertThat(response.getReportType()).isEqualTo(ReportType.SUMMER_PEAK_CHECK.name());
        assertThat(response.getVersion()).isEqualTo("1.0.0");
        assertThat(response.getConfigJson()).isEqualTo("{\"sections\":[]}");
        assertThat(response.getEnabled()).isTrue();
        assertThat(response.getCreatedBy()).isEqualTo("admin");
        assertThat(Path.of(response.getFilePath())).exists();
        assertThat(inserted.get().getFilePath()).isEqualTo(response.getFilePath());
    }

    @Test
    void uploadRejectsInvalidFileAndInvalidConfig() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "template.txt", "text/plain", "plain".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(
                textFile, "Template", ReportType.SUMMER_PEAK_CHECK.name(), "1.0.0", null, true, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only .docx");

        assertThatThrownBy(() -> service.upload(
                docx("template.docx", "body"), "Template", ReportType.SUMMER_PEAK_CHECK.name(), "1.0.0",
                "{invalid", true, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void updateChangesOnlySubmittedFields() {
        ReportTemplateEntity entity = template("tpl-1", "Old", "1.0.0", "template.docx");
        when(templateMapper.selectById("tpl-1")).thenReturn(entity);

        TemplateUpdateRequest request = new TemplateUpdateRequest();
        request.setName("  New Name  ");
        request.setVersion("2.0.0");
        request.setConfigJson("");
        request.setEnabled(false);

        TemplateResponse response = service.update("tpl-1", request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getReportType()).isEqualTo(ReportType.SUMMER_PEAK_CHECK.name());
        assertThat(response.getVersion()).isEqualTo("2.0.0");
        assertThat(response.getConfigJson()).isNull();
        assertThat(response.getEnabled()).isFalse();
        verify(templateMapper).updateById(entity);
    }

    @Test
    void replaceFileStoresNewFileAndDeletesOldFile() throws IOException {
        Path oldFile = tempDir.resolve("old-template.docx");
        Files.writeString(oldFile, "old", StandardCharsets.UTF_8);
        ReportTemplateEntity entity = template("tpl-1", "Template", "1.0.0", oldFile.toString());
        when(templateMapper.selectById("tpl-1")).thenReturn(entity);

        TemplateResponse response = service.replaceFile("tpl-1", docx("new-template.docx", "new"));

        assertThat(Path.of(response.getFilePath())).exists();
        assertThat(response.getFilePath()).isNotEqualTo(oldFile.toString());
        assertThat(oldFile).doesNotExist();
        verify(templateMapper).updateById(entity);
    }

    @Test
    void configDownloadAndDeleteUseStoredTemplate() throws IOException {
        Path file = tempDir.resolve("stored-template.docx");
        Files.writeString(file, "stored", StandardCharsets.UTF_8);
        ReportTemplateEntity entity = template("tpl-1", "Template", "1.0.0", file.toString());
        entity.setConfigJson(null);
        when(templateMapper.selectById("tpl-1")).thenReturn(entity);

        assertThat(service.getConfig("tpl-1")).isEmpty();

        TemplateConfigRequest request = new TemplateConfigRequest();
        request.setConfigJson("{\"outline\":true}");
        TemplateResponse updated = service.updateConfig("tpl-1", request);
        assertThat(updated.getConfigJson()).isEqualTo("{\"outline\":true}");

        TemplateFileResource resource = service.loadFile("tpl-1");
        assertThat(resource.fileName()).isEqualTo("stored-template.docx");
        assertThat(resource.contentLength()).isEqualTo(Files.size(file));
        assertThat(resource.resource().exists()).isTrue();

        service.delete("tpl-1");

        verify(templateMapper).deleteById("tpl-1");
        assertThat(file).doesNotExist();
    }

    private MockMultipartFile docx(String fileName, String content) {
        return new MockMultipartFile(
                "file",
                fileName,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private ReportTemplateEntity template(String id, String name, String version, String filePath) {
        ReportTemplateEntity entity = new ReportTemplateEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setReportType(ReportType.SUMMER_PEAK_CHECK.name());
        entity.setVersion(version);
        entity.setFilePath(filePath);
        entity.setConfigJson("{\"sections\":[]}");
        entity.setEnabled(true);
        entity.setCreatedBy("admin");
        entity.setCreatedAt(LocalDateTime.now().minusDays(1));
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
