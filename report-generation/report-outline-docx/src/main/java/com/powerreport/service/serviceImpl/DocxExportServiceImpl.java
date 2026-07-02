package com.powerreport.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.config.ReportExportProperties;
import com.powerreport.dto.ReportDocxExportRequest;
import com.powerreport.dto.ReportFileResponse;
import com.powerreport.dto.StoredReportFile;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportFileEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.enums.CaptionNumberingMode;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportFileMapper;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import com.powerreport.mapper.ReportTemplateMapper;
import com.powerreport.service.DocxExportService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocxExportServiceImpl implements DocxExportService {

    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("^!\\[(.*?)]\\((.*?)\\)\\s*$");
    private static final String DEFAULT_OWNER = "local_user";
    private static final String ROOT_PARENT_KEY = "__ROOT__";
    private static final String MINIO_PREFIX = "minio://";

    private final ReportExportProperties properties;
    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportSectionMapper sectionMapper;
    private final ReportFileMapper fileMapper;
    private final ReportTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Override
    @Transactional
    public ReportFileResponse exportReport(String reportId, ReportDocxExportRequest request) throws IOException {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null || Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("报告不存在或已删除: " + reportId);
        }

        List<ReportOutlineNodeEntity> outlineNodes = outlineNodeMapper.selectList(
                new LambdaQueryWrapper<ReportOutlineNodeEntity>()
                        .eq(ReportOutlineNodeEntity::getReportId, reportId)
        );
        List<ReportSectionEntity> sections = sectionMapper.selectList(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
        );

        Path outputPath = nextOutputPath(report.getName());
        CaptionCounter captions = new CaptionCounter(
                request.getFigureNumberingMode(),
                request.getTableNumberingMode()
        );
        TemplateContext templateContext = resolveTemplateContext(report, request);
        ExportStyle style = buildExportStyle(templateContext.template());

        try (XWPFDocument document = createDocument(templateContext)) {
            replaceTemplatePlaceholders(document, report, style);
            setupPage(document, style);
            setupHeaderFooter(document, report, style);
            if (style.renderReportHeader()) {
                addReportHeader(
                        document,
                        report.getName(),
                        reportTypeLabel(report.getReportType()),
                        report.getPowerPlant(),
                        report.getSpecialty(),
                        report.getReportYear(),
                        report.getSubject(),
                        style
                );
            }
            renderSavedDraft(document, outlineNodes, sections, Boolean.TRUE.equals(request.getIncludeEmptySections()), captions, style);
            writeDocument(document, outputPath);
        }

        ReportFileEntity file = new ReportFileEntity();
        file.setId(UUID.randomUUID().toString());
        file.setReportId(reportId);
        file.setFileName(outputPath.getFileName().toString());
        file.setFilePath(outputPath.toAbsolutePath().toString());
        file.setFileSize(Files.size(outputPath));
        file.setSha256(sha256(outputPath));
        file.setCreatedBy(DEFAULT_OWNER);
        fileMapper.insert(file);

        report.setStatus(ReportStatus.EXPORTED.name());
        reportMapper.updateById(report);

        return new ReportFileResponse(
                file.getId(),
                reportId,
                file.getFileName(),
                file.getFileSize(),
                file.getSha256(),
                "/api/reports/files/" + file.getId() + "/download"
        );
    }

    @Override
    public StoredReportFile getFileForDownload(String fileId) throws IOException {
        ReportFileEntity file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new IllegalArgumentException("导出文件不存在: " + fileId);
        }

        Path path = Path.of(file.getFilePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("导出文件已丢失: " + fileId);
        }

        long fileSize = file.getFileSize() == null || file.getFileSize() <= 0
                ? Files.size(path)
                : file.getFileSize();
        return new StoredReportFile(file.getId(), file.getFileName(), path, fileSize);
    }

    private TemplateContext resolveTemplateContext(ReportEntity report, ReportDocxExportRequest request) {
        if (!properties.isTemplateEnabled() || Boolean.FALSE.equals(request.getUseTemplate())) {
            return new TemplateContext(null);
        }

        ReportTemplateEntity template = null;
        if (StringUtils.hasText(request.getTemplateId())) {
            template = templateMapper.selectById(request.getTemplateId());
            if (template == null || !Boolean.TRUE.equals(template.getEnabled())) {
                throw new IllegalArgumentException("模板不存在或未启用: " + request.getTemplateId());
            }
            if (StringUtils.hasText(template.getReportType())
                    && StringUtils.hasText(report.getReportType())
                    && !Objects.equals(template.getReportType(), report.getReportType())) {
                throw new IllegalArgumentException("模板报告类型与当前报告不匹配");
            }
        } else if (StringUtils.hasText(report.getReportType())) {
            template = templateMapper.selectOne(new LambdaQueryWrapper<ReportTemplateEntity>()
                    .eq(ReportTemplateEntity::getReportType, report.getReportType())
                    .eq(ReportTemplateEntity::getEnabled, true)
                    .orderByDesc(ReportTemplateEntity::getUpdatedAt)
                    .last("LIMIT 1"));
        }

        return new TemplateContext(template);
    }

    private XWPFDocument createDocument(TemplateContext templateContext) {
        ReportTemplateEntity template = templateContext.template();
        if (template == null) {
            return new XWPFDocument();
        }

        try (InputStream inputStream = openTemplateInputStream(template)) {
            log.info("Applying DOCX template: id={}, name={}, storageType={}",
                    template.getId(), template.getName(), resolveStorageType(template));
            return new XWPFDocument(inputStream);
        } catch (Exception ex) {
            log.warn("Failed to apply DOCX template, fallback to blank document. templateId={}, reason={}",
                    template.getId(), ex.getMessage());
            return new XWPFDocument();
        }
    }

    private InputStream openTemplateInputStream(ReportTemplateEntity template) throws Exception {
        TemplateLocation location = resolveTemplateLocation(template);
        if ("MINIO".equalsIgnoreCase(location.storageType())) {
            if (!StringUtils.hasText(location.bucketName()) || !StringUtils.hasText(location.objectName())) {
                throw new IllegalStateException("template MinIO object is empty");
            }
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(location.bucketName())
                    .object(location.objectName())
                    .build());
        }

        Path path = resolveLocalTemplatePath(location.filePath());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("template file does not exist: " + path);
        }
        return Files.newInputStream(path);
    }

    private TemplateLocation resolveTemplateLocation(ReportTemplateEntity template) {
        MinioObject minioObject = parseMinioPath(template.getFilePath());
        String bucketName = StringUtils.hasText(template.getBucketName())
                ? template.getBucketName()
                : minioObject.bucketName();
        String objectName = StringUtils.hasText(template.getObjectName())
                ? template.getObjectName()
                : minioObject.objectName();
        String storageType = resolveStorageType(template);
        return new TemplateLocation(storageType, template.getFilePath(), bucketName, objectName);
    }

    private String resolveStorageType(ReportTemplateEntity template) {
        if (StringUtils.hasText(template.getBucketName()) && StringUtils.hasText(template.getObjectName())) {
            return "MINIO";
        }
        if (StringUtils.hasText(template.getFilePath()) && template.getFilePath().startsWith(MINIO_PREFIX)) {
            return "MINIO";
        }
        if (StringUtils.hasText(template.getStorageType())) {
            return template.getStorageType();
        }
        return "LOCAL";
    }

    private MinioObject parseMinioPath(String filePath) {
        if (!StringUtils.hasText(filePath) || !filePath.startsWith(MINIO_PREFIX)) {
            return new MinioObject(null, null);
        }
        String path = filePath.substring(MINIO_PREFIX.length());
        int slashIndex = path.indexOf('/');
        if (slashIndex <= 0 || slashIndex == path.length() - 1) {
            return new MinioObject(null, null);
        }
        return new MinioObject(path.substring(0, slashIndex), path.substring(slashIndex + 1));
    }

    private Path resolveLocalTemplatePath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalStateException("template file path is empty");
        }
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path directPath = path.toAbsolutePath().normalize();
        if (Files.exists(directPath)) {
            return directPath;
        }
        return Paths.get(properties.getTemplateStorageDir()).toAbsolutePath().normalize().resolve(filePath).normalize();
    }

    private ExportStyle buildExportStyle(ReportTemplateEntity template) {
        ExportStyle style = new ExportStyle(
                properties.getBodyFont(),
                properties.getTitleFont(),
                properties.getTitleFontSize(),
                properties.getHeading1FontSize(),
                properties.getHeading2FontSize(),
                properties.getHeading3FontSize(),
                properties.getBodyFontSize(),
                properties.getCaptionFontSize(),
                properties.getTableFontSize(),
                properties.getLineSpacing(),
                properties.getFirstLineIndentTwips(),
                properties.getMarginTopTwips(),
                properties.getMarginBottomTwips(),
                properties.getMarginLeftTwips(),
                properties.getMarginRightTwips(),
                properties.isPreferTemplateHeaderFooter(),
                properties.isHeaderEnabled(),
                properties.isFooterEnabled(),
                properties.isRenderReportHeader(),
                properties.getHeaderText(),
                properties.getFooterText()
        );
        if (template == null || !StringUtils.hasText(template.getConfigJson())) {
            return style;
        }

        try {
            JsonNode root = objectMapper.readTree(template.getConfigJson());
            JsonNode styleNode = root.path("style");
            JsonNode fontsNode = root.path("fonts");
            JsonNode pageNode = root.path("page");
            JsonNode marginsNode = root.path("margins");
            JsonNode paragraphNode = root.path("paragraph");
            JsonNode headerNode = root.path("header");
            JsonNode footerNode = root.path("footer");
            return new ExportStyle(
                    text(styleNode, "bodyFont", text(fontsNode, "bodyFont", style.bodyFont())),
                    text(styleNode, "titleFont", text(fontsNode, "titleFont", style.titleFont())),
                    integer(styleNode, "titleFontSize", integer(fontsNode, "titleSize", style.titleFontSize())),
                    integer(styleNode, "heading1FontSize", integer(fontsNode, "heading1Size", style.heading1FontSize())),
                    integer(styleNode, "heading2FontSize", integer(fontsNode, "heading2Size", style.heading2FontSize())),
                    integer(styleNode, "heading3FontSize", style.heading3FontSize()),
                    integer(styleNode, "bodyFontSize", integer(fontsNode, "bodySize", style.bodyFontSize())),
                    integer(styleNode, "captionFontSize", style.captionFontSize()),
                    integer(styleNode, "tableFontSize", style.tableFontSize()),
                    doubleValue(paragraphNode, "lineSpacing", style.lineSpacing()),
                    firstLineIndentTwips(paragraphNode, style.firstLineIndentTwips()),
                    longValue(pageNode, "marginTopTwips", twipsFromCm(marginsNode, "topCm", style.marginTopTwips())),
                    longValue(pageNode, "marginBottomTwips", twipsFromCm(marginsNode, "bottomCm", style.marginBottomTwips())),
                    longValue(pageNode, "marginLeftTwips", twipsFromCm(marginsNode, "leftCm", style.marginLeftTwips())),
                    longValue(pageNode, "marginRightTwips", twipsFromCm(marginsNode, "rightCm", style.marginRightTwips())),
                    bool(root, "preferTemplateHeaderFooter", style.preferTemplateHeaderFooter()),
                    bool(headerNode, "enabled", style.headerEnabled()),
                    bool(footerNode, "enabled", style.footerEnabled()),
                    bool(root, "renderReportHeader", style.renderReportHeader()),
                    text(headerNode, "text", style.headerText()),
                    text(footerNode, "text", style.footerText())
            );
        } catch (IOException ex) {
            log.warn("Template configJson is invalid, fallback to default style. templateId={}, reason={}",
                    template.getId(), ex.getMessage());
            return style;
        }
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText() : defaultValue;
    }

    private int integer(JsonNode node, String field, int defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToInt() ? value.asInt() : defaultValue;
    }

    private long longValue(JsonNode node, String field, long defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.canConvertToLong() ? value.asLong() : defaultValue;
    }

    private double doubleValue(JsonNode node, String field, double defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : defaultValue;
    }

    private int firstLineIndentTwips(JsonNode paragraphNode, int defaultValue) {
        JsonNode twips = paragraphNode == null ? null : paragraphNode.get("firstLineIndentTwips");
        if (twips != null && twips.canConvertToInt()) {
            return twips.asInt();
        }
        JsonNode chars = paragraphNode == null ? null : paragraphNode.get("firstLineIndentChars");
        if (chars != null && chars.isNumber()) {
            return (int) Math.round(chars.asDouble() * 280);
        }
        return defaultValue;
    }

    private long twipsFromCm(JsonNode node, String field, long defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isNumber()) {
            return defaultValue;
        }
        return Math.round(value.asDouble() * 1440 / 2.54);
    }

    private boolean bool(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : defaultValue;
    }

    private void replaceTemplatePlaceholders(XWPFDocument document, ReportEntity report, ExportStyle style) {
        Map<String, String> replacements = reportPlaceholders(report);
        document.getParagraphs().forEach(paragraph -> replaceParagraphPlaceholders(paragraph, replacements, style));
        document.getTables().forEach(table -> replaceTablePlaceholders(table, replacements, style));
        document.getHeaderList().forEach(header -> {
            header.getParagraphs().forEach(paragraph -> replaceParagraphPlaceholders(paragraph, replacements, style));
            header.getTables().forEach(table -> replaceTablePlaceholders(table, replacements, style));
        });
        document.getFooterList().forEach(footer -> {
            footer.getParagraphs().forEach(paragraph -> replaceParagraphPlaceholders(paragraph, replacements, style));
            footer.getTables().forEach(table -> replaceTablePlaceholders(table, replacements, style));
        });
    }

    private void replaceTablePlaceholders(
            XWPFTable table,
            Map<String, String> replacements,
            ExportStyle style
    ) {
        table.getRows().forEach(row ->
                row.getTableCells().forEach(cell -> {
                    cell.getParagraphs().forEach(paragraph -> replaceParagraphPlaceholders(paragraph, replacements, style));
                    cell.getTables().forEach(nested -> replaceTablePlaceholders(nested, replacements, style));
                }));
    }

    private void replaceParagraphPlaceholders(
            XWPFParagraph paragraph,
            Map<String, String> replacements,
            ExportStyle style
    ) {
        String text = paragraph.getText();
        if (!StringUtils.hasText(text)) {
            return;
        }
        String replaced = replacePlaceholders(text, replacements);
        if (Objects.equals(text, replaced)) {
            return;
        }
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        addRun(paragraph, replaced, false, style.bodyFontSize(), style.bodyFont());
    }

    private Map<String, String> reportPlaceholders(ReportEntity report) {
        Map<String, String> values = new HashMap<>();
        values.put("reportName", nullToEmpty(report.getName()));
        values.put("reportType", nullToEmpty(reportTypeLabel(report.getReportType())));
        values.put("reportTypeCode", nullToEmpty(report.getReportType()));
        values.put("powerPlant", nullToEmpty(report.getPowerPlant()));
        values.put("specialty", nullToEmpty(report.getSpecialty()));
        values.put("reportYear", report.getReportYear() == null ? "" : String.valueOf(report.getReportYear()));
        values.put("subject", nullToEmpty(report.getSubject()));
        values.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return values;
    }

    private String replacePlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private void setupHeaderFooter(XWPFDocument document, ReportEntity report, ExportStyle style) {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(document, section);
        Map<String, String> values = reportPlaceholders(report);

        if (style.headerEnabled() && shouldCreateHeader(document, style)) {
            XWPFHeader header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);
            XWPFParagraph paragraph = header.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            addTextWithPageFields(paragraph, replacePlaceholders(style.headerText(), values), style);
        }

        if (style.footerEnabled() && shouldCreateFooter(document, style)) {
            XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
            XWPFParagraph paragraph = footer.createParagraph();
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            addTextWithPageFields(paragraph, replacePlaceholders(style.footerText(), values), style);
        }
    }

    private boolean shouldCreateHeader(XWPFDocument document, ExportStyle style) {
        return !style.preferTemplateHeaderFooter() || document.getHeaderList().isEmpty();
    }

    private boolean shouldCreateFooter(XWPFDocument document, ExportStyle style) {
        return !style.preferTemplateHeaderFooter() || document.getFooterList().isEmpty();
    }

    private void addTextWithPageFields(XWPFParagraph paragraph, String text, ExportStyle style) {
        String value = text == null ? "" : text;
        int index = 0;
        while (index < value.length()) {
            int pageIndex = value.indexOf("{page}", index);
            int pagesIndex = value.indexOf("{pages}", index);
            int nextIndex = nextTokenIndex(pageIndex, pagesIndex);
            if (nextIndex < 0) {
                addRun(paragraph, value.substring(index), false, style.captionFontSize(), style.bodyFont());
                return;
            }
            if (nextIndex > index) {
                addRun(paragraph, value.substring(index, nextIndex), false, style.captionFontSize(), style.bodyFont());
            }
            if (pageIndex == nextIndex) {
                addFieldRun(paragraph, "PAGE", "1", style);
                index = pageIndex + "{page}".length();
            } else {
                addFieldRun(paragraph, "NUMPAGES", "1", style);
                index = pagesIndex + "{pages}".length();
            }
        }
    }

    private int nextTokenIndex(int pageIndex, int pagesIndex) {
        if (pageIndex < 0) {
            return pagesIndex;
        }
        if (pagesIndex < 0) {
            return pageIndex;
        }
        return Math.min(pageIndex, pagesIndex);
    }

    private void addFieldRun(XWPFParagraph paragraph, String instruction, String fallback, ExportStyle style) {
        XWPFRun begin = paragraph.createRun();
        begin.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        setRunFont(begin, style.bodyFont(), style.captionFontSize(), false);

        XWPFRun instr = paragraph.createRun();
        instr.getCTR().addNewInstrText().setStringValue(instruction);
        setRunFont(instr, style.bodyFont(), style.captionFontSize(), false);

        XWPFRun separate = paragraph.createRun();
        separate.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        setRunFont(separate, style.bodyFont(), style.captionFontSize(), false);

        XWPFRun text = paragraph.createRun();
        text.setText(fallback);
        setRunFont(text, style.bodyFont(), style.captionFontSize(), false);

        XWPFRun end = paragraph.createRun();
        end.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        setRunFont(end, style.bodyFont(), style.captionFontSize(), false);
    }

    private void renderSavedDraft(
            XWPFDocument document,
            List<ReportOutlineNodeEntity> outlineNodes,
            List<ReportSectionEntity> sections,
            boolean includeEmptySections,
            CaptionCounter captions,
            ExportStyle style
    ) {
        Map<String, ReportSectionEntity> sectionByOutlineId = new HashMap<>();
        Map<String, ReportSectionEntity> sectionByNumber = new HashMap<>();
        for (ReportSectionEntity section : sections) {
            if (StringUtils.hasText(section.getOutlineNodeId())) {
                sectionByOutlineId.putIfAbsent(section.getOutlineNodeId(), section);
            }
            if (StringUtils.hasText(section.getNumber())) {
                sectionByNumber.putIfAbsent(section.getNumber(), section);
            }
        }

        if (outlineNodes.isEmpty()) {
            sections.stream()
                    .sorted(Comparator.comparing(ReportSectionEntity::getNumber, this::compareSectionNumber))
                    .forEach(section -> {
                        addHeading(document, section.getNumber() + " " + section.getTitle(), 1, style);
                        addMarkdownContent(document, section.getContentMarkdown(), section.getNumber(), captions, style);
                    });
            return;
        }

        Map<String, List<ReportOutlineNodeEntity>> childrenByParent = buildOutlineTree(outlineNodes);
        // TODO: 后端 2/AI 模块写入 report_sections.content_markdown 后，这里会自动把正文拼进 Word。
        renderOutlineChildren(
                document,
                childrenByParent.getOrDefault(ROOT_PARENT_KEY, List.of()),
                childrenByParent,
                sectionByOutlineId,
                sectionByNumber,
                includeEmptySections,
                captions,
                style
        );
    }

    private void renderOutlineChildren(
            XWPFDocument document,
            List<ReportOutlineNodeEntity> nodes,
            Map<String, List<ReportOutlineNodeEntity>> childrenByParent,
            Map<String, ReportSectionEntity> sectionByOutlineId,
            Map<String, ReportSectionEntity> sectionByNumber,
            boolean includeEmptySections,
            CaptionCounter captions,
            ExportStyle style
    ) {
        for (ReportOutlineNodeEntity node : nodes) {
            List<ReportOutlineNodeEntity> children = childrenByParent.getOrDefault(node.getId(), List.of());
            ReportSectionEntity section = sectionByOutlineId.getOrDefault(node.getId(), sectionByNumber.get(node.getNumber()));
            String content = section == null ? "" : section.getContentMarkdown();
            boolean hasContent = StringUtils.hasText(content);
            boolean leafNode = children.isEmpty();

            if (includeEmptySections || hasContent || !children.isEmpty()) {
                addHeading(document, node.getNumber() + " " + node.getTitle(), node.getLevel(), style);
            }
            if (leafNode && hasContent) {
                addMarkdownContent(document, content, node.getNumber(), captions, style);
            }
            renderOutlineChildren(
                    document,
                    children,
                    childrenByParent,
                    sectionByOutlineId,
                    sectionByNumber,
                    includeEmptySections,
                    captions,
                    style
            );
        }
    }

    private Map<String, List<ReportOutlineNodeEntity>> buildOutlineTree(List<ReportOutlineNodeEntity> nodes) {
        Map<String, List<ReportOutlineNodeEntity>> childrenByParent = new LinkedHashMap<>();
        for (ReportOutlineNodeEntity node : nodes) {
            childrenByParent
                    .computeIfAbsent(parentKey(node.getParentId()), ignored -> new ArrayList<>())
                    .add(node);
        }
        childrenByParent.values().forEach(list -> list.sort(this::compareOutlineNode));
        return childrenByParent;
    }

    private String parentKey(String parentId) {
        return StringUtils.hasText(parentId) ? parentId : ROOT_PARENT_KEY;
    }

    private int compareOutlineNode(ReportOutlineNodeEntity left, ReportOutlineNodeEntity right) {
        int sortCompare = Comparator
                .comparing(
                        ReportOutlineNodeEntity::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)
                )
                .compare(left, right);
        if (sortCompare != 0) {
            return sortCompare;
        }
        return compareSectionNumber(left.getNumber(), right.getNumber());
    }

    private int compareSectionNumber(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parseNumberPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parseNumberPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return left.compareTo(right);
    }

    private int parseNumberPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("\\D", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void setupPage(XWPFDocument document, ExportStyle style) {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar pageMar = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(style.marginTopTwips()));
        pageMar.setBottom(BigInteger.valueOf(style.marginBottomTwips()));
        pageMar.setLeft(BigInteger.valueOf(style.marginLeftTwips()));
        pageMar.setRight(BigInteger.valueOf(style.marginRightTwips()));
    }

    private void addReportHeader(
            XWPFDocument document,
            String name,
            String reportType,
            String powerPlant,
            String specialty,
            Integer reportYear,
            String subject,
            ExportStyle style
    ) {
        addTitle(document, name, style);
        addInfoParagraph(document, "报告类型：" + nullToEmpty(reportType), style);
        addInfoParagraph(document, "电厂：" + nullToEmpty(powerPlant), style);
        addInfoParagraph(document, "专业：" + nullToEmpty(specialty), style);
        addInfoParagraph(document, "年份：" + (reportYear == null ? "" : reportYear), style);
        addInfoParagraph(document, "主题：" + nullToEmpty(subject), style);
    }

    private void addTitle(XWPFDocument document, String text, ExportStyle style) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(240);
        addRun(paragraph, nullToEmpty(text), true, style.titleFontSize(), style.titleFont());
    }

    private void addHeading(XWPFDocument document, String text, Integer level, ExportStyle style) {
        int safeLevel = level == null || level <= 0 ? 1 : level;
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + Math.min(safeLevel, 3));
        paragraph.setSpacingBefore(safeLevel == 1 ? 240 : 120);
        paragraph.setSpacingAfter(120);

        int fontSize = switch (safeLevel) {
            case 1 -> style.heading1FontSize();
            case 2 -> style.heading2FontSize();
            default -> style.heading3FontSize();
        };
        addRun(paragraph, nullToEmpty(text), true, fontSize, style.titleFont());
    }

    private void addInfoParagraph(XWPFDocument document, String text, ExportStyle style) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        addRun(paragraph, nullToEmpty(text), false, style.bodyFontSize(), style.bodyFont());
    }

    private void addBodyParagraph(XWPFDocument document, String text, ExportStyle style) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingBetween(style.lineSpacing());
        paragraph.setIndentationFirstLine(style.firstLineIndentTwips());
        addRun(paragraph, nullToEmpty(text), false, style.bodyFontSize(), style.bodyFont());
    }

    private void addCaption(XWPFDocument document, String text, ExportStyle style) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(80);
        addRun(paragraph, nullToEmpty(text), false, style.captionFontSize(), style.bodyFont());
    }

    private void addRun(XWPFParagraph paragraph, String text, boolean bold, int fontSize, String fontFamily) {
        XWPFRun run = paragraph.createRun();
        setRunFont(run, fontFamily, fontSize, bold);
        run.setText(text == null ? "" : text);
    }

    private void setRunFont(XWPFRun run, String fontFamily, int fontSize, boolean bold) {
        run.setFontFamily(fontFamily);
        run.setFontSize(fontSize);
        run.setBold(bold);
        setEastAsiaFont(run, fontFamily);
    }

    private void setEastAsiaFont(XWPFRun run, String fontFamily) {
        CTRPr runProperties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = runProperties.sizeOfRFontsArray() > 0
                ? runProperties.getRFontsArray(0)
                : runProperties.addNewRFonts();
        fonts.setAscii(fontFamily);
        fonts.setHAnsi(fontFamily);
        fonts.setEastAsia(fontFamily);
        fonts.setCs(fontFamily);
    }

    private void addMarkdownContent(
            XWPFDocument document,
            String markdown,
            String sectionNumber,
            CaptionCounter captions,
            ExportStyle style
    ) {
        String content = markdown == null || markdown.isBlank() ? "" : markdown;
        String[] lines = content.split("\\R", -1);
        List<String> tableLines = new ArrayList<>();
        String pendingTableCaption = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (isMarkdownTableLine(trimmed)) {
                tableLines.add(trimmed);
                continue;
            }

            if (!tableLines.isEmpty()) {
                flushTable(document, tableLines, pendingTableCaption, sectionNumber, captions, style);
                tableLines.clear();
                pendingTableCaption = null;
            }

            String tableCaption = parseTableCaption(trimmed);
            if (tableCaption != null) {
                pendingTableCaption = tableCaption;
                continue;
            }

            if (tryAddFigureCaption(document, trimmed, sectionNumber, captions, style)) {
                continue;
            }

            int markdownHeadingLevel = markdownHeadingLevel(trimmed);
            if (markdownHeadingLevel > 0) {
                addHeading(document, trimmed.substring(markdownHeadingLevel).trim(), Math.min(markdownHeadingLevel + 1, 3), style);
            } else if (!trimmed.isBlank()) {
                addBodyParagraph(document, trimmed, style);
            }
        }

        if (!tableLines.isEmpty()) {
            flushTable(document, tableLines, pendingTableCaption, sectionNumber, captions, style);
        }
    }

    private boolean tryAddFigureCaption(
            XWPFDocument document,
            String trimmed,
            String sectionNumber,
            CaptionCounter captions,
            ExportStyle style
    ) {
        Matcher matcher = MARKDOWN_IMAGE.matcher(trimmed);
        if (!matcher.matches()) {
            return false;
        }
        String caption = matcher.group(1);
        String prefix = captions.nextFigure(sectionNumber);
        // TODO: 如果后续恢复 MinIO 或统一文件服务，可在这里根据图片地址嵌入真实图片；当前先生成图题注。
        addCaption(document, StringUtils.hasText(caption) ? prefix + " " + caption.strip() : prefix, style);
        return true;
    }

    private void flushTable(
            XWPFDocument document,
            List<String> tableLines,
            String tableCaption,
            String sectionNumber,
            CaptionCounter captions,
            ExportStyle style
    ) {
        List<List<String>> rows = tableLines.stream()
                .filter(line -> !isMarkdownSeparatorLine(line))
                .map(this::parseMarkdownRow)
                .filter(row -> !row.isEmpty())
                .toList();
        if (rows.isEmpty()) {
            return;
        }

        String prefix = captions.nextTable(sectionNumber);
        addCaption(document, StringUtils.hasText(tableCaption) ? prefix + " " + tableCaption.strip() : prefix, style);

        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = document.createTable(rows.size(), columns);
        table.setWidth("100%");
        applyThreeLineTableStyle(table);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow tableRow = table.getRow(rowIndex);
            List<String> row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < columns; colIndex++) {
                String value = colIndex < row.size() ? row.get(colIndex) : "";
                setCellText(tableRow.getCell(colIndex), value, rowIndex == 0, style);
            }
        }
    }

    private void applyThreeLineTableStyle(XWPFTable table) {
        CTTblPr tableProperties = table.getCTTbl().getTblPr();
        if (tableProperties == null) {
            tableProperties = table.getCTTbl().addNewTblPr();
        }
        CTTblBorders borders = tableProperties.isSetTblBorders()
                ? tableProperties.getTblBorders()
                : tableProperties.addNewTblBorders();

        setBorder(borders.isSetTop() ? borders.getTop() : borders.addNewTop());
        setBorder(borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom());
        setNilBorder(borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft());
        setNilBorder(borders.isSetRight() ? borders.getRight() : borders.addNewRight());
        setNilBorder(borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV());
        setNilBorder(borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH());

        if (table.getNumberOfRows() > 0) {
            for (XWPFTableCell cell : table.getRow(0).getTableCells()) {
                CTTcPr cellProperties = cell.getCTTc().isSetTcPr()
                        ? cell.getCTTc().getTcPr()
                        : cell.getCTTc().addNewTcPr();
                CTTcBorders cellBorders = cellProperties.isSetTcBorders()
                        ? cellProperties.getTcBorders()
                        : cellProperties.addNewTcBorders();
                setBorder(cellBorders.isSetBottom() ? cellBorders.getBottom() : cellBorders.addNewBottom());
            }
        }
    }

    private void setBorder(CTBorder border) {
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(8));
        border.setColor("000000");
        border.setSpace(BigInteger.ZERO);
    }

    private void setNilBorder(CTBorder border) {
        border.setVal(STBorder.NIL);
        border.setSz(BigInteger.ZERO);
        border.setSpace(BigInteger.ZERO);
    }

    private void setCellText(XWPFTableCell cell, String value, boolean header, ExportStyle style) {
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(0);
        addRun(paragraph, value, header, style.tableFontSize(), header ? style.titleFont() : style.bodyFont());
    }

    private boolean isMarkdownTableLine(String trimmed) {
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.indexOf('|') != trimmed.lastIndexOf('|');
    }

    private boolean isMarkdownSeparatorLine(String line) {
        String stripped = line.replace("|", "").replace(":", "").replace("-", "").trim();
        return stripped.isEmpty() && line.contains("---");
    }

    private String parseTableCaption(String trimmed) {
        for (String prefix : List.of("表：", "表:", "表格：", "表格:", "Table:", "table:")) {
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    private int markdownHeadingLevel(String trimmed) {
        if (!trimmed.startsWith("#")) {
            return 0;
        }
        int count = 0;
        while (count < trimmed.length() && trimmed.charAt(count) == '#') {
            count++;
        }
        if (count > 0 && count <= 6 && trimmed.length() > count && Character.isWhitespace(trimmed.charAt(count))) {
            return count;
        }
        return 0;
    }

    private List<String> parseMarkdownRow(String line) {
        String stripped = line;
        if (stripped.startsWith("|")) {
            stripped = stripped.substring(1);
        }
        if (stripped.endsWith("|")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        String[] cells = stripped.split("\\|", -1);
        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cell.trim());
        }
        return result;
    }

    private Path nextOutputPath(String reportName) throws IOException {
        Path exportDir = Path.of(properties.getExportDir());
        Files.createDirectories(exportDir);
        String fileName = safeFileName(reportName) + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ".docx";
        return exportDir.resolve(fileName);
    }

    private void writeDocument(XWPFDocument document, Path outputPath) throws IOException {
        try (var out = Files.newOutputStream(outputPath)) {
            document.write(out);
        }
    }

    private String reportTypeLabel(String reportType) {
        if (!StringUtils.hasText(reportType)) {
            return "";
        }
        try {
            return ReportType.valueOf(reportType).getLabel();
        } catch (IllegalArgumentException ex) {
            return reportType;
        }
    }

    private String safeFileName(String value) {
        String safe = ILLEGAL_FILENAME_CHARS.matcher(value == null ? "report" : value)
                .replaceAll("_")
                .strip();
        return safe.isBlank() ? "report" : safe;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path);
                 DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                digestInput.transferTo(OutputStreamDiscard.INSTANCE);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to calculate file sha256", ex);
        }
    }

    private record TemplateContext(ReportTemplateEntity template) {
    }

    private record TemplateLocation(
            String storageType,
            String filePath,
            String bucketName,
            String objectName
    ) {
    }

    private record MinioObject(String bucketName, String objectName) {
    }

    private record ExportStyle(
            String bodyFont,
            String titleFont,
            int titleFontSize,
            int heading1FontSize,
            int heading2FontSize,
            int heading3FontSize,
            int bodyFontSize,
            int captionFontSize,
            int tableFontSize,
            double lineSpacing,
            int firstLineIndentTwips,
            long marginTopTwips,
            long marginBottomTwips,
            long marginLeftTwips,
            long marginRightTwips,
            boolean preferTemplateHeaderFooter,
            boolean headerEnabled,
            boolean footerEnabled,
            boolean renderReportHeader,
            String headerText,
            String footerText
    ) {
    }

    private static class CaptionCounter {
        private final CaptionNumberingMode figureMode;
        private final CaptionNumberingMode tableMode;
        private final Map<String, Integer> figureSectionCounters = new HashMap<>();
        private final Map<String, Integer> tableSectionCounters = new HashMap<>();
        private int globalFigureCounter = 0;
        private int globalTableCounter = 0;

        CaptionCounter(CaptionNumberingMode figureMode, CaptionNumberingMode tableMode) {
            this.figureMode = figureMode == null ? CaptionNumberingMode.GLOBAL : figureMode;
            this.tableMode = tableMode == null ? CaptionNumberingMode.GLOBAL : tableMode;
        }

        String nextFigure(String sectionNumber) {
            return "图 " + nextNumber(figureMode, figureSectionCounters, true, sectionNumber);
        }

        String nextTable(String sectionNumber) {
            return "表 " + nextNumber(tableMode, tableSectionCounters, false, sectionNumber);
        }

        private String nextNumber(
                CaptionNumberingMode mode,
                Map<String, Integer> sectionCounters,
                boolean figure,
                String sectionNumber
        ) {
            if (mode == CaptionNumberingMode.SECTION) {
                String chapter = chapterNumber(sectionNumber);
                int index = sectionCounters.merge(chapter, 1, Integer::sum);
                return chapter + "." + index;
            }
            if (figure) {
                return String.valueOf(++globalFigureCounter);
            }
            return String.valueOf(++globalTableCounter);
        }

        private String chapterNumber(String sectionNumber) {
            if (!StringUtils.hasText(sectionNumber)) {
                return "1";
            }
            String[] parts = sectionNumber.split("\\.");
            return StringUtils.hasText(parts[0]) ? parts[0] : "1";
        }
    }

    private static class OutputStreamDiscard extends java.io.OutputStream {
        private static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();

        @Override
        public void write(int b) {
            // discard
        }
    }
}
