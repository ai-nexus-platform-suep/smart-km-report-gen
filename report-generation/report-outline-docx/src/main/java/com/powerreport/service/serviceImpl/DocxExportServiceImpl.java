package com.powerreport.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.powerreport.service.DocxExportService;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DocxExportServiceImpl implements DocxExportService {

    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("^!\\[(.*?)]\\((.*?)\\)\\s*$");
    private static final String BODY_FONT = "仿宋_GB2312";
    private static final String TITLE_FONT = "黑体";
    private static final String DEFAULT_OWNER = "local_user";
    private static final String ROOT_PARENT_KEY = "__ROOT__";

    private final ReportExportProperties properties;
    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportSectionMapper sectionMapper;
    private final ReportFileMapper fileMapper;

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

        try (XWPFDocument document = new XWPFDocument()) {
            setupPage(document);
            addReportHeader(
                    document,
                    report.getName(),
                    reportTypeLabel(report.getReportType()),
                    report.getPowerPlant(),
                    report.getSpecialty(),
                    report.getReportYear(),
                    report.getSubject()
            );
            renderSavedDraft(document, outlineNodes, sections, Boolean.TRUE.equals(request.getIncludeEmptySections()), captions);
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

    private void renderSavedDraft(
            XWPFDocument document,
            List<ReportOutlineNodeEntity> outlineNodes,
            List<ReportSectionEntity> sections,
            boolean includeEmptySections,
            CaptionCounter captions
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
                        addHeading(document, section.getNumber() + " " + section.getTitle(), 1);
                        addMarkdownContent(document, section.getContentMarkdown(), section.getNumber(), captions);
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
                captions
        );
    }

    private void renderOutlineChildren(
            XWPFDocument document,
            List<ReportOutlineNodeEntity> nodes,
            Map<String, List<ReportOutlineNodeEntity>> childrenByParent,
            Map<String, ReportSectionEntity> sectionByOutlineId,
            Map<String, ReportSectionEntity> sectionByNumber,
            boolean includeEmptySections,
            CaptionCounter captions
    ) {
        for (ReportOutlineNodeEntity node : nodes) {
            List<ReportOutlineNodeEntity> children = childrenByParent.getOrDefault(node.getId(), List.of());
            ReportSectionEntity section = sectionByOutlineId.getOrDefault(node.getId(), sectionByNumber.get(node.getNumber()));
            String content = section == null ? "" : section.getContentMarkdown();
            boolean hasContent = StringUtils.hasText(content);

            if (includeEmptySections || hasContent || !children.isEmpty()) {
                addHeading(document, node.getNumber() + " " + node.getTitle(), node.getLevel());
            }
            if (hasContent) {
                addMarkdownContent(document, content, node.getNumber(), captions);
            }
            renderOutlineChildren(
                    document,
                    children,
                    childrenByParent,
                    sectionByOutlineId,
                    sectionByNumber,
                    includeEmptySections,
                    captions
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

    private void setupPage(XWPFDocument document) {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar pageMar = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(1440));
        pageMar.setBottom(BigInteger.valueOf(1440));
        pageMar.setLeft(BigInteger.valueOf(1584));
        pageMar.setRight(BigInteger.valueOf(1584));
    }

    private void addReportHeader(
            XWPFDocument document,
            String name,
            String reportType,
            String powerPlant,
            String specialty,
            Integer reportYear,
            String subject
    ) {
        addTitle(document, name);
        addInfoParagraph(document, "报告类型：" + nullToEmpty(reportType));
        addInfoParagraph(document, "电厂：" + nullToEmpty(powerPlant));
        addInfoParagraph(document, "专业：" + nullToEmpty(specialty));
        addInfoParagraph(document, "年份：" + (reportYear == null ? "" : reportYear));
        addInfoParagraph(document, "主题：" + nullToEmpty(subject));
    }

    private void addTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(240);
        addRun(paragraph, nullToEmpty(text), true, 22, TITLE_FONT);
    }

    private void addHeading(XWPFDocument document, String text, Integer level) {
        int safeLevel = level == null || level <= 0 ? 1 : level;
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + Math.min(safeLevel, 3));
        paragraph.setSpacingBefore(safeLevel == 1 ? 240 : 120);
        paragraph.setSpacingAfter(120);

        int fontSize = switch (safeLevel) {
            case 1 -> 16;
            case 2 -> 14;
            default -> 12;
        };
        addRun(paragraph, nullToEmpty(text), true, fontSize, TITLE_FONT);
    }

    private void addInfoParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(80);
        addRun(paragraph, nullToEmpty(text), false, 12, BODY_FONT);
    }

    private void addBodyParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        paragraph.setSpacingBetween(1.5);
        paragraph.setIndentationFirstLine(560);
        addRun(paragraph, nullToEmpty(text), false, 12, BODY_FONT);
    }

    private void addCaption(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingBefore(80);
        paragraph.setSpacingAfter(80);
        addRun(paragraph, nullToEmpty(text), false, 10, BODY_FONT);
    }

    private void addRun(XWPFParagraph paragraph, String text, boolean bold, int fontSize, String fontFamily) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(fontFamily);
        run.setFontSize(fontSize);
        run.setBold(bold);
        setEastAsiaFont(run, fontFamily);
        run.setText(text == null ? "" : text);
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
            CaptionCounter captions
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
                flushTable(document, tableLines, pendingTableCaption, sectionNumber, captions);
                tableLines.clear();
                pendingTableCaption = null;
            }

            String tableCaption = parseTableCaption(trimmed);
            if (tableCaption != null) {
                pendingTableCaption = tableCaption;
                continue;
            }

            if (tryAddFigureCaption(document, trimmed, sectionNumber, captions)) {
                continue;
            }

            int markdownHeadingLevel = markdownHeadingLevel(trimmed);
            if (markdownHeadingLevel > 0) {
                addHeading(document, trimmed.substring(markdownHeadingLevel).trim(), Math.min(markdownHeadingLevel + 1, 3));
            } else if (!trimmed.isBlank()) {
                addBodyParagraph(document, trimmed);
            }
        }

        if (!tableLines.isEmpty()) {
            flushTable(document, tableLines, pendingTableCaption, sectionNumber, captions);
        }
    }

    private boolean tryAddFigureCaption(
            XWPFDocument document,
            String trimmed,
            String sectionNumber,
            CaptionCounter captions
    ) {
        Matcher matcher = MARKDOWN_IMAGE.matcher(trimmed);
        if (!matcher.matches()) {
            return false;
        }
        String caption = matcher.group(1);
        String prefix = captions.nextFigure(sectionNumber);
        // TODO: 如果后续恢复 MinIO 或统一文件服务，可在这里根据图片地址嵌入真实图片；当前先生成图题注。
        addCaption(document, StringUtils.hasText(caption) ? prefix + " " + caption.strip() : prefix);
        return true;
    }

    private void flushTable(
            XWPFDocument document,
            List<String> tableLines,
            String tableCaption,
            String sectionNumber,
            CaptionCounter captions
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
        addCaption(document, StringUtils.hasText(tableCaption) ? prefix + " " + tableCaption.strip() : prefix);

        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = document.createTable(rows.size(), columns);
        table.setWidth("100%");
        applyThreeLineTableStyle(table);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow tableRow = table.getRow(rowIndex);
            List<String> row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < columns; colIndex++) {
                String value = colIndex < row.size() ? row.get(colIndex) : "";
                setCellText(tableRow.getCell(colIndex), value, rowIndex == 0);
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

    private void setCellText(XWPFTableCell cell, String value, boolean header) {
        while (!cell.getParagraphs().isEmpty()) {
            cell.removeParagraph(0);
        }
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(0);
        addRun(paragraph, value, header, 11, header ? TITLE_FONT : BODY_FONT);
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
