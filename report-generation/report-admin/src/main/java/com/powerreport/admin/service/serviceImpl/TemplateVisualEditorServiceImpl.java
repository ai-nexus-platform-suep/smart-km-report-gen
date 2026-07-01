package com.powerreport.admin.service.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.admin.dto.TemplateConfigFieldSchema;
import com.powerreport.admin.dto.TemplateConfigSchemaResponse;
import com.powerreport.admin.dto.TemplateOutlineNodeDto;
import com.powerreport.admin.dto.TemplateResponse;
import com.powerreport.admin.dto.TemplateVisualConfigDto;
import com.powerreport.admin.service.TemplateVisualEditorService;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.enums.CaptionNumberingMode;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportTemplateMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TemplateVisualEditorServiceImpl implements TemplateVisualEditorService {

    private final ReportTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    @Override
    public TemplateConfigSchemaResponse getConfigSchema() {
        TemplateVisualConfigDto defaults = buildDefaultConfig(ReportType.SUMMER_PEAK_CHECK);

        TemplateConfigSchemaResponse schema = new TemplateConfigSchemaResponse();
        schema.setDefaultConfig(defaults);
        schema.setFields(List.of(
                field("fonts.titleFont", "标题字体", "string", "报告封面与章节标题字体", defaults.getFonts().getTitleFont()),
                field("fonts.bodyFont", "正文字体", "string", "正文与说明文字字体", defaults.getFonts().getBodyFont()),
                field("fonts.titleSize", "封面标题字号", "number", "封面主标题字号（磅）", defaults.getFonts().getTitleSize()),
                field("fonts.heading1Size", "一级标题字号", "number", "章标题字号（磅）", defaults.getFonts().getHeading1Size()),
                field("fonts.heading2Size", "二级标题字号", "number", "节标题字号（磅）", defaults.getFonts().getHeading2Size()),
                field("fonts.bodySize", "正文字号", "number", "正文字号（磅）", defaults.getFonts().getBodySize()),
                field("margins.topCm", "上边距(cm)", "number", "页面上边距，单位厘米", defaults.getMargins().getTopCm()),
                field("margins.bottomCm", "下边距(cm)", "number", "页面下边距，单位厘米", defaults.getMargins().getBottomCm()),
                field("margins.leftCm", "左边距(cm)", "number", "页面左边距，单位厘米", defaults.getMargins().getLeftCm()),
                field("margins.rightCm", "右边距(cm)", "number", "页面右边距，单位厘米", defaults.getMargins().getRightCm()),
                field("paragraph.lineSpacing", "行距", "number", "正文行距倍数", defaults.getParagraph().getLineSpacing()),
                field("paragraph.firstLineIndentChars", "首行缩进(字)", "number", "正文首行缩进字符数", defaults.getParagraph().getFirstLineIndentChars()),
                enumField("caption.figureNumberingMode", "图编号模式", "图题编号方式",
                        defaults.getCaption().getFigureNumberingMode(),
                        List.of(CaptionNumberingMode.GLOBAL.name(), CaptionNumberingMode.SECTION.name())),
                enumField("caption.tableNumberingMode", "表编号模式", "表题编号方式",
                        defaults.getCaption().getTableNumberingMode(),
                        List.of(CaptionNumberingMode.GLOBAL.name(), CaptionNumberingMode.SECTION.name())),
                field("outline", "大纲结构", "outline-tree", "模板默认章节结构，支持拖拽编辑", defaults.getOutline())
        ));
        return schema;
    }

    @Override
    public TemplateVisualConfigDto getDefaultConfig(String reportType) {
        ReportType type = parseReportType(reportType);
        return buildDefaultConfig(type);
    }

    @Override
    public TemplateVisualConfigDto getVisualConfig(String templateId) {
        ReportTemplateEntity entity = requireTemplate(templateId);
        if (!StringUtils.hasText(entity.getConfigJson())) {
            return buildDefaultConfig(ReportType.valueOf(entity.getReportType()));
        }
        try {
            TemplateVisualConfigDto config = objectMapper.readValue(entity.getConfigJson(), TemplateVisualConfigDto.class);
            if (config.getOutline() == null || config.getOutline().isEmpty()) {
                config.setOutline(buildDefaultConfig(ReportType.valueOf(entity.getReportType())).getOutline());
            }
            return config;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("template configJson is not a valid visual config");
        }
    }

    @Override
    public TemplateResponse updateVisualConfig(String templateId, TemplateVisualConfigDto config) {
        validateVisualConfig(config);
        ReportTemplateEntity entity = requireTemplate(templateId);
        try {
            entity.setConfigJson(objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize visual config");
        }
        entity.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(entity);
        return toResponse(entity);
    }

    private void validateVisualConfig(TemplateVisualConfigDto config) {
        if (config == null) {
            throw new IllegalArgumentException("visual config is required");
        }
        if (config.getFonts() == null || !StringUtils.hasText(config.getFonts().getTitleFont())) {
            throw new IllegalArgumentException("fonts.titleFont is required");
        }
        if (config.getFonts() == null || !StringUtils.hasText(config.getFonts().getBodyFont())) {
            throw new IllegalArgumentException("fonts.bodyFont is required");
        }
        validateNumberingMode(config.getCaption().getFigureNumberingMode(), "figureNumberingMode");
        validateNumberingMode(config.getCaption().getTableNumberingMode(), "tableNumberingMode");
        if (config.getOutline() == null || config.getOutline().isEmpty()) {
            throw new IllegalArgumentException("outline must contain at least one node");
        }
        validateOutlineNodes(config.getOutline(), 1);
    }

    private void validateOutlineNodes(List<TemplateOutlineNodeDto> nodes, int depth) {
        if (depth > 5) {
            throw new IllegalArgumentException("outline depth exceeds maximum of 5");
        }
        for (int i = 0; i < nodes.size(); i++) {
            TemplateOutlineNodeDto node = nodes.get(i);
            if (!StringUtils.hasText(node.getTitle())) {
                throw new IllegalArgumentException("outline node title is required");
            }
            if (!StringUtils.hasText(node.getNumber())) {
                node.setNumber(String.valueOf(i + 1));
            }
            if (node.getLevel() == null || node.getLevel() <= 0) {
                node.setLevel(depth);
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                validateOutlineNodes(node.getChildren(), depth + 1);
            }
        }
    }

    private void validateNumberingMode(String mode, String fieldName) {
        if (!StringUtils.hasText(mode)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            CaptionNumberingMode.valueOf(mode.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported " + fieldName + ": " + mode);
        }
    }

    private TemplateVisualConfigDto buildDefaultConfig(ReportType reportType) {
        TemplateVisualConfigDto config = new TemplateVisualConfigDto();
        config.setOutline(reportType == ReportType.COAL_INVENTORY_AUDIT
                ? coalInventoryAuditOutline()
                : summerPeakCheckOutline());
        return config;
    }

    private List<TemplateOutlineNodeDto> summerPeakCheckOutline() {
        return List.of(
                node("1", "检查概况", 1, List.of(
                        node("1.1", "检查背景", 2),
                        node("1.2", "检查范围", 2),
                        node("1.3", "检查依据", 2)
                )),
                node("2", "设备运行与安全保障情况", 1, List.of(
                        node("2.1", "主设备运行情况", 2),
                        node("2.2", "电气设备检查情况", 2),
                        node("2.3", "热控及保护系统检查情况", 2)
                )),
                node("3", "迎峰度夏重点措施落实情况", 1, List.of(
                        node("3.1", "负荷预测与运行安排", 2),
                        node("3.2", "防汛防高温措施", 2),
                        node("3.3", "应急预案与值班安排", 2)
                )),
                node("4", "存在问题", 1, List.of(
                        node("4.1", "设备隐患", 2),
                        node("4.2", "管理薄弱环节", 2)
                )),
                node("5", "整改建议", 1, List.of(
                        node("5.1", "整改措施", 2),
                        node("5.2", "责任分工与完成时限", 2)
                )),
                node("6", "结论", 1)
        );
    }

    private List<TemplateOutlineNodeDto> coalInventoryAuditOutline() {
        return List.of(
                node("1", "审计概况", 1, List.of(
                        node("1.1", "审计背景", 2),
                        node("1.2", "审计范围", 2),
                        node("1.3", "审计依据", 2)
                )),
                node("2", "煤炭库存管理情况", 1, List.of(
                        node("2.1", "库存台账情况", 2),
                        node("2.2", "入厂煤管理情况", 2),
                        node("2.3", "耗用与盘点情况", 2)
                )),
                node("3", "审计发现", 1, List.of(
                        node("3.1", "账实一致性问题", 2),
                        node("3.2", "计量与验收问题", 2),
                        node("3.3", "内控管理问题", 2)
                )),
                node("4", "数据分析", 1, List.of(
                        node("4.1", "库存变化分析", 2),
                        node("4.2", "采购与耗用匹配分析", 2)
                )),
                node("5", "整改建议", 1, List.of(
                        node("5.1", "管理改进建议", 2),
                        node("5.2", "内控优化建议", 2)
                )),
                node("6", "审计结论", 1)
        );
    }

    private TemplateOutlineNodeDto node(String number, String title, int level) {
        return node(number, title, level, List.of());
    }

    private TemplateOutlineNodeDto node(String number, String title, int level, List<TemplateOutlineNodeDto> children) {
        TemplateOutlineNodeDto node = new TemplateOutlineNodeDto();
        node.setId(UUID.randomUUID().toString());
        node.setNumber(number);
        node.setTitle(title);
        node.setLevel(level);
        node.setPromptHint(null);
        node.setChildren(new ArrayList<>(children));
        return node;
    }

    private ReportTemplateEntity requireTemplate(String templateId) {
        if (!StringUtils.hasText(templateId)) {
            throw new IllegalArgumentException("templateId is required");
        }
        ReportTemplateEntity entity = templateMapper.selectById(templateId);
        if (entity == null) {
            throw new IllegalArgumentException("template does not exist");
        }
        return entity;
    }

    private ReportType parseReportType(String reportType) {
        if (!StringUtils.hasText(reportType)) {
            throw new IllegalArgumentException("reportType is required");
        }
        try {
            return ReportType.valueOf(reportType.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported reportType: " + reportType);
        }
    }

    private TemplateConfigFieldSchema field(String key, String label, String type, String description, Object defaultValue) {
        TemplateConfigFieldSchema schema = new TemplateConfigFieldSchema();
        schema.setKey(key);
        schema.setLabel(label);
        schema.setType(type);
        schema.setDescription(description);
        schema.setDefaultValue(defaultValue);
        return schema;
    }

    private TemplateConfigFieldSchema enumField(
            String key,
            String label,
            String description,
            Object defaultValue,
            List<String> options
    ) {
        TemplateConfigFieldSchema schema = field(key, label, "enum", description, defaultValue);
        schema.setOptions(options);
        return schema;
    }

    private TemplateResponse toResponse(ReportTemplateEntity entity) {
        TemplateResponse response = new TemplateResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setReportType(entity.getReportType());
        response.setVersion(entity.getVersion());
        response.setFilePath(entity.getFilePath());
        response.setConfigJson(entity.getConfigJson());
        response.setEnabled(entity.getEnabled());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
