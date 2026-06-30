package com.powerreport.service.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.config.ReportAiProperties;
import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineConfirmResponse;
import com.powerreport.dto.OutlineGenerateRequest;
import com.powerreport.dto.OutlineGenerateResponse;
import com.powerreport.dto.OutlineNodeResponse;
import com.powerreport.dto.OutlineTempState;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.service.OutlineService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class OutlineServiceImpl implements OutlineService {

    private static final String OUTLINE_TEMP_KEY_PREFIX = "report-outline-docx:outline:";
    private static final String DEFAULT_OWNER = "local_user";

    private final ReportAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final RestTemplate restTemplate;

    public OutlineServiceImpl(
            ReportAiProperties aiProperties,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            ReportMapper reportMapper,
            ReportOutlineNodeMapper outlineNodeMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.reportMapper = reportMapper;
        this.outlineNodeMapper = outlineNodeMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .build();
    }

    @Override
    public List<OutlineNodeResponse> buildOutline(ReportType reportType) {
        if (reportType == ReportType.COAL_INVENTORY_AUDIT) {
            return normalizeOutline(coalInventoryAuditOutline());
        }
        return normalizeOutline(summerPeakCheckOutline());
    }

    @Override
    public OutlineGenerateResponse generateOutline(OutlineGenerateRequest request) {
        String source = "AI";
        List<OutlineNodeResponse> outline;
        try {
            if (!StringUtils.hasText(aiProperties.getOutlineUrl())) {
                throw new IllegalStateException("AI outline URL is not configured");
            }
            outline = requestAiOutline(request);
        } catch (RuntimeException ex) {
            // TODO: 正式联调验收时建议把 app.ai.fallback-enabled 改为 false，避免 AI 失败时仍使用本地模板。
            if (!aiProperties.isFallbackEnabled()) {
                throw ex;
            }
            log.warn("AI outline generation failed, fallback to local outline template. reason={}", ex.getMessage());
            source = "LOCAL_TEMPLATE";
            outline = buildOutline(request.getReportType());
        }

        String tempId = UUID.randomUUID().toString();
        long ttlSeconds = Math.max(aiProperties.getOutlineTempTtlSeconds(), 60);
        OutlineTempState state = buildTempState(tempId, source, request, outline);
        saveTempState(state, ttlSeconds);

        OutlineGenerateResponse response = new OutlineGenerateResponse();
        response.setTempId(tempId);
        response.setSource(source);
        response.setExpireSeconds(ttlSeconds);
        response.setOutline(outline);
        return response;
    }

    @Override
    @Transactional
    public OutlineConfirmResponse confirmOutline(OutlineConfirmRequest request) {
        List<OutlineNodeResponse> outline = resolveConfirmedOutline(request);
        if (outline.isEmpty()) {
            throw new IllegalArgumentException("确认保存的大纲不能为空");
        }

        List<OutlineNodeResponse> normalizedOutline = normalizeOutline(outline);
        String reportId = UUID.randomUUID().toString();
        int outlineCount = countOutlineNodes(normalizedOutline);

        ReportEntity report = new ReportEntity();
        report.setId(reportId);
        report.setName(resolveReportName(request));
        report.setReportType(request.getReportType().name());
        report.setSubject(request.getSubject());
        report.setSpecialty(request.getSpecialty());
        report.setPowerPlant(request.getPowerPlant());
        report.setReportYear(request.getReportYear());
        report.setStatus(ReportStatus.OUTLINE_READY.name());
        report.setOwnerName(DEFAULT_OWNER);
        report.setTotalSections(outlineCount);
        report.setCompletedSections(0);
        report.setDeleted(false);
        reportMapper.insert(report);

        saveOutlineNodes(reportId, null, normalizedOutline);

        if (StringUtils.hasText(request.getTempId())) {
            redisTemplate.delete(tempKey(request.getTempId()));
        }

        return new OutlineConfirmResponse(reportId, ReportStatus.OUTLINE_READY.name(), outlineCount, normalizedOutline);
    }

    private List<OutlineNodeResponse> requestAiOutline(OutlineGenerateRequest request) {
        // TODO: 与 AI 全栈确认最终协议后，如字段名变化，只需要调整这里的请求体和 parseAiOutline。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportType", request.getReportType().name());
        body.put("reportTypeLabel", request.getReportType().getLabel());
        body.put("subject", request.getSubject());
        body.put("name", request.getName());
        body.put("specialty", request.getSpecialty());
        body.put("powerPlant", request.getPowerPlant());
        body.put("reportYear", request.getReportYear());
        body.put("context", request.getContext());

        String responseBody;
        try {
            responseBody = restTemplate.postForObject(aiProperties.getOutlineUrl(), body, String.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("AI 大纲接口调用失败: " + ex.getMessage(), ex);
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("AI 大纲接口返回为空");
        }
        return parseAiOutline(responseBody);
    }

    private List<OutlineNodeResponse> parseAiOutline(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode outlineNode = extractOutlineNode(root);
            if (outlineNode == null || outlineNode.isNull()) {
                throw new IllegalStateException("AI 大纲接口未返回 outline/nodes/sections 字段");
            }

            List<OutlineNodeResponse> result = new ArrayList<>();
            if (outlineNode.isArray()) {
                for (int i = 0; i < outlineNode.size(); i++) {
                    result.add(toOutlineNode(outlineNode.get(i), String.valueOf(i + 1), 1));
                }
            } else if (looksLikeSingleOutlineNode(outlineNode)) {
                result.add(toOutlineNode(outlineNode, "1", 1));
            } else {
                throw new IllegalStateException("AI 大纲接口返回格式无法解析");
            }
            return normalizeOutline(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("AI 大纲接口返回不是合法 JSON", ex);
        }
    }

    private JsonNode extractOutlineNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isArray() || looksLikeSingleOutlineNode(root)) {
            return root;
        }

        for (String wrapper : List.of("data", "result")) {
            JsonNode wrapped = root.get(wrapper);
            JsonNode extracted = extractOutlineNode(wrapped);
            if (extracted != null) {
                return extracted;
            }
        }

        for (String field : List.of("outline", "nodes", "sections", "items")) {
            JsonNode direct = root.get(field);
            if (direct != null && !direct.isNull()) {
                return direct;
            }
        }
        return null;
    }

    private boolean looksLikeSingleOutlineNode(JsonNode node) {
        return node != null
                && node.isObject()
                && (node.has("title") || node.has("name") || node.has("heading"))
                && !node.has("success")
                && !node.has("code");
    }

    private OutlineNodeResponse toOutlineNode(JsonNode node, String defaultNumber, int defaultLevel) {
        OutlineNodeResponse response = new OutlineNodeResponse();
        response.setId(firstText(node, "id", "nodeId"));
        response.setNumber(firstText(node, "number", "no", "index"));
        response.setTitle(firstText(node, "title", "name", "heading"));
        response.setLevel(firstInt(node, "level", "depth"));
        response.setPromptHint(firstText(node, "promptHint", "prompt", "hint"));

        if (!StringUtils.hasText(response.getNumber())) {
            response.setNumber(defaultNumber);
        }
        if (response.getLevel() == null || response.getLevel() <= 0) {
            response.setLevel(defaultLevel);
        }
        if (!StringUtils.hasText(response.getTitle())) {
            response.setTitle("未命名章节");
        }

        JsonNode childrenNode = firstNode(node, "children", "sections", "items");
        if (childrenNode != null && childrenNode.isArray()) {
            List<OutlineNodeResponse> children = new ArrayList<>();
            for (int i = 0; i < childrenNode.size(); i++) {
                String childNumber = response.getNumber() + "." + (i + 1);
                children.add(toOutlineNode(childrenNode.get(i), childNumber, response.getLevel() + 1));
            }
            response.setChildren(children);
        }
        return response;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (StringUtils.hasText(text)) {
                    return text.strip();
                }
            }
        }
        return null;
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    private JsonNode firstNode(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private OutlineTempState buildTempState(
            String tempId,
            String source,
            OutlineGenerateRequest request,
            List<OutlineNodeResponse> outline
    ) {
        OutlineTempState state = new OutlineTempState();
        state.setTempId(tempId);
        state.setName(request.getName());
        state.setReportType(request.getReportType());
        state.setSubject(request.getSubject());
        state.setSpecialty(request.getSpecialty());
        state.setPowerPlant(request.getPowerPlant());
        state.setReportYear(request.getReportYear());
        state.setSource(source);
        state.setGeneratedAt(LocalDateTime.now());
        state.setOutline(outline);
        return state;
    }

    private void saveTempState(OutlineTempState state, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(tempKey(state.getTempId()), json, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("大纲临时状态序列化失败", ex);
        }
    }

    private List<OutlineNodeResponse> resolveConfirmedOutline(OutlineConfirmRequest request) {
        if (request.getOutline() != null && !request.getOutline().isEmpty()) {
            return request.getOutline();
        }
        if (!StringUtils.hasText(request.getTempId())) {
            throw new IllegalArgumentException("outline 为空时必须传 tempId");
        }

        String json = redisTemplate.opsForValue().get(tempKey(request.getTempId()));
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("大纲临时状态不存在或已过期");
        }
        try {
            OutlineTempState state = objectMapper.readValue(json, OutlineTempState.class);
            return state.getOutline() == null ? List.of() : state.getOutline();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("大纲临时状态反序列化失败", ex);
        }
    }

    private String tempKey(String tempId) {
        return OUTLINE_TEMP_KEY_PREFIX + tempId;
    }

    private String resolveReportName(OutlineConfirmRequest request) {
        if (StringUtils.hasText(request.getName())) {
            return request.getName().strip();
        }
        return request.getSubject().strip() + "报告";
    }

    private int saveOutlineNodes(String reportId, String parentId, List<OutlineNodeResponse> nodes) {
        int count = 0;
        for (int i = 0; i < nodes.size(); i++) {
            OutlineNodeResponse node = nodes.get(i);
            String nodeId = UUID.randomUUID().toString();
            node.setId(nodeId);

            ReportOutlineNodeEntity entity = new ReportOutlineNodeEntity();
            entity.setId(nodeId);
            entity.setReportId(reportId);
            entity.setParentId(parentId);
            entity.setLevel(node.getLevel());
            entity.setSortOrder(i + 1);
            entity.setNumber(node.getNumber());
            entity.setTitle(node.getTitle());
            entity.setPromptHint(node.getPromptHint());
            outlineNodeMapper.insert(entity);

            count++;
            count += saveOutlineNodes(reportId, nodeId, safeChildren(node));
        }
        return count;
    }

    private int countOutlineNodes(List<OutlineNodeResponse> nodes) {
        int count = 0;
        for (OutlineNodeResponse node : nodes) {
            count++;
            count += countOutlineNodes(safeChildren(node));
        }
        return count;
    }

    private List<OutlineNodeResponse> normalizeOutline(List<OutlineNodeResponse> nodes) {
        List<OutlineNodeResponse> normalized = new ArrayList<>();
        if (nodes == null) {
            return normalized;
        }
        for (int i = 0; i < nodes.size(); i++) {
            normalized.add(normalizeNode(nodes.get(i), String.valueOf(i + 1), 1));
        }
        return normalized;
    }

    private OutlineNodeResponse normalizeNode(OutlineNodeResponse node, String defaultNumber, int defaultLevel) {
        OutlineNodeResponse normalized = new OutlineNodeResponse();
        normalized.setId(StringUtils.hasText(node.getId()) ? node.getId() : UUID.randomUUID().toString());
        normalized.setNumber(StringUtils.hasText(node.getNumber()) ? node.getNumber().strip() : defaultNumber);
        normalized.setTitle(StringUtils.hasText(node.getTitle()) ? node.getTitle().strip() : "未命名章节");
        normalized.setLevel(node.getLevel() == null || node.getLevel() <= 0 ? defaultLevel : node.getLevel());
        normalized.setPromptHint(node.getPromptHint());

        List<OutlineNodeResponse> children = new ArrayList<>();
        List<OutlineNodeResponse> rawChildren = safeChildren(node);
        for (int i = 0; i < rawChildren.size(); i++) {
            children.add(normalizeNode(
                    rawChildren.get(i),
                    normalized.getNumber() + "." + (i + 1),
                    normalized.getLevel() + 1
            ));
        }
        normalized.setChildren(children);
        return normalized;
    }

    private List<OutlineNodeResponse> safeChildren(OutlineNodeResponse node) {
        return node.getChildren() == null ? List.of() : node.getChildren();
    }

    private List<OutlineNodeResponse> summerPeakCheckOutline() {
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

    private List<OutlineNodeResponse> coalInventoryAuditOutline() {
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

    private OutlineNodeResponse node(String number, String title, int level) {
        return node(number, title, level, List.of());
    }

    private OutlineNodeResponse node(
            String number,
            String title,
            int level,
            List<OutlineNodeResponse> children
    ) {
        OutlineNodeResponse response = new OutlineNodeResponse();
        response.setId(UUID.randomUUID().toString());
        response.setNumber(number);
        response.setTitle(title);
        response.setLevel(level);
        response.setPromptHint(null);
        response.setChildren(new ArrayList<>(children));
        return response;
    }
}
