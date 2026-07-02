package com.powerreport.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.config.ReportAiProperties;
import com.powerreport.dto.OutlineConfirmRequest;
import com.powerreport.dto.OutlineConfirmResponse;
import com.powerreport.dto.OutlineDraftRequest;
import com.powerreport.dto.OutlineDraftResponse;
import com.powerreport.dto.OutlineGenerateRequest;
import com.powerreport.dto.OutlineGenerateResponse;
import com.powerreport.dto.OutlineNodeResponse;
import com.powerreport.dto.OutlineTablePlan;
import com.powerreport.dto.OutlineTempState;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.enums.ContentGenerationMode;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportTemplateMapper;
import com.powerreport.service.OutlineService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final String ROOT_PARENT_KEY = "__ROOT__";
    private static final String TABLE_PLAN_PREFIX = "<!--TABLE_PLAN_JSON_BASE64:";
    private static final String TABLE_PLAN_SUFFIX = "-->";

    private final ReportAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportTemplateMapper templateMapper;
    private final RestTemplate restTemplate;

    public OutlineServiceImpl(
            ReportAiProperties aiProperties,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            ReportMapper reportMapper,
            ReportOutlineNodeMapper outlineNodeMapper,
            ReportTemplateMapper templateMapper,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.reportMapper = reportMapper;
        this.outlineNodeMapper = outlineNodeMapper;
        this.templateMapper = templateMapper;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .build();
    }

    @Override
    public List<OutlineNodeResponse> buildOutline(ReportType reportType) {
        return buildTemplateOrDefaultOutline(reportType, null);
    }

    @Override
    public OutlineGenerateResponse generateOutline(OutlineGenerateRequest request) {
        String source;
        List<OutlineNodeResponse> outline;
        if (request.getGenerationMode() == ContentGenerationMode.TEMPLATE) {
            source = "TEMPLATE";
            outline = buildTemplateOrDefaultOutline(request.getReportType(), request.getTemplateId());
        } else {
            source = "AI";
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
                outline = buildTemplateOrDefaultOutline(request.getReportType(), request.getTemplateId());
            }
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
        if (StringUtils.hasText(request.getReportId())) {
            return confirmDraftOutline(request);
        }

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

    @Override
    @Transactional
    public OutlineDraftResponse createDraftOutline(OutlineDraftRequest request) {
        List<OutlineNodeResponse> normalizedOutline = normalizeOutline(resolveDraftOutline(request));
        int outlineCount = countOutlineNodes(normalizedOutline);
        String reportId = UUID.randomUUID().toString();

        ReportEntity report = new ReportEntity();
        report.setId(reportId);
        applyDraftMetadata(report, request, outlineCount);
        report.setOwnerName(DEFAULT_OWNER);
        report.setCompletedSections(0);
        report.setDeleted(false);
        reportMapper.insert(report);

        saveOutlineNodes(reportId, null, normalizedOutline);
        deleteTempState(request.getTempId());
        return toDraftResponse(report, normalizedOutline);
    }

    @Override
    @Transactional
    public OutlineDraftResponse updateDraftOutline(String reportId, OutlineDraftRequest request) {
        ReportEntity report = requireReport(reportId);
        ensureDraftStatus(report);

        List<OutlineNodeResponse> normalizedOutline = normalizeOutline(resolveDraftOutline(request));
        int outlineCount = countOutlineNodes(normalizedOutline);

        applyDraftMetadata(report, request, outlineCount);
        report.setCompletedSections(0);
        reportMapper.updateById(report);
        replaceOutlineNodes(reportId, normalizedOutline);

        deleteTempState(request.getTempId());
        return toDraftResponse(report, normalizedOutline);
    }

    @Override
    public OutlineDraftResponse getSavedOutline(String reportId) {
        ReportEntity report = requireReport(reportId);
        return toDraftResponse(report, loadOutlineTree(reportId));
    }

    private OutlineConfirmResponse confirmDraftOutline(OutlineConfirmRequest request) {
        ReportEntity report = requireReport(request.getReportId());
        ensureDraftStatus(report);

        List<OutlineNodeResponse> outline = resolveConfirmedOutline(request);
        if (outline.isEmpty()) {
            throw new IllegalArgumentException("确认保存的大纲不能为空");
        }

        List<OutlineNodeResponse> normalizedOutline = normalizeOutline(outline);
        int outlineCount = countOutlineNodes(normalizedOutline);

        report.setName(resolveReportName(request));
        report.setReportType(request.getReportType().name());
        report.setSubject(request.getSubject());
        report.setSpecialty(request.getSpecialty());
        report.setPowerPlant(request.getPowerPlant());
        report.setReportYear(request.getReportYear());
        report.setStatus(ReportStatus.OUTLINE_READY.name());
        report.setTotalSections(outlineCount);
        report.setCompletedSections(0);
        reportMapper.updateById(report);

        replaceOutlineNodes(report.getId(), normalizedOutline);
        deleteTempState(request.getTempId());
        return new OutlineConfirmResponse(report.getId(), ReportStatus.OUTLINE_READY.name(), outlineCount, normalizedOutline);
    }

    private List<OutlineNodeResponse> requestAiOutline(OutlineGenerateRequest request) {
        // TODO: 与 AI 全栈确认最终协议后，如字段名变化，只需要调整这里的请求体和 parseAiOutline。
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportType", request.getReportType().name());
        body.put("reportTypeLabel", request.getReportType().getLabel());
        body.put("generationMode", request.getGenerationMode() == null ? ContentGenerationMode.AI.name() : request.getGenerationMode().name());
        body.put("templateId", request.getTemplateId());
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

    private List<OutlineNodeResponse> buildTemplateOrDefaultOutline(ReportType reportType, String templateId) {
        return resolveTemplateOutline(reportType, templateId)
                .map(this::normalizeOutline)
                .orElseGet(() -> normalizeOutline(defaultOutline(reportType)));
    }

    private Optional<List<OutlineNodeResponse>> resolveTemplateOutline(ReportType reportType, String templateId) {
        try {
            ReportTemplateEntity template = null;
            if (StringUtils.hasText(templateId)) {
                template = templateMapper.selectById(templateId);
                if (template == null || !Boolean.TRUE.equals(template.getEnabled())) {
                    throw new IllegalArgumentException("模板不存在或未启用: " + templateId);
                }
                if (StringUtils.hasText(template.getReportType())
                        && !reportType.name().equals(template.getReportType())) {
                    throw new IllegalArgumentException("模板报告类型与当前报告类型不匹配");
                }
            } else {
                template = templateMapper.selectOne(new LambdaQueryWrapper<ReportTemplateEntity>()
                        .eq(ReportTemplateEntity::getReportType, reportType.name())
                        .eq(ReportTemplateEntity::getEnabled, true)
                        .orderByDesc(ReportTemplateEntity::getUpdatedAt)
                        .last("LIMIT 1"));
            }

            if (template == null || !StringUtils.hasText(template.getConfigJson())) {
                return Optional.empty();
            }
            return parseTemplateOutline(template.getConfigJson());
        } catch (RuntimeException ex) {
            if (StringUtils.hasText(templateId)) {
                throw ex;
            }
            log.warn("Failed to load template outline, fallback to built-in outline. reportType={}, reason={}",
                    reportType, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<List<OutlineNodeResponse>> parseTemplateOutline(String configJson) {
        try {
            JsonNode root = objectMapper.readTree(configJson);
            JsonNode outlineNode = root.get("outline");
            if (outlineNode == null || !outlineNode.isArray() || outlineNode.isEmpty()) {
                return Optional.empty();
            }

            List<OutlineNodeResponse> result = new ArrayList<>();
            for (int i = 0; i < outlineNode.size(); i++) {
                result.add(toOutlineNode(outlineNode.get(i), String.valueOf(i + 1), 1));
            }
            return Optional.of(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("模板 configJson 不是合法 JSON", ex);
        }
    }

    private List<OutlineNodeResponse> defaultOutline(ReportType reportType) {
        if (reportType == ReportType.COAL_INVENTORY_AUDIT) {
            return coalInventoryAuditOutline();
        }
        return summerPeakCheckOutline();
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
        response.setTables(parseTablePlans(firstNode(node, "tables", "tablePlans", "tableJson")));

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

    private List<OutlineNodeResponse> resolveDraftOutline(OutlineDraftRequest request) {
        if (request.getOutline() != null && !request.getOutline().isEmpty()) {
            return request.getOutline();
        }
        if (!StringUtils.hasText(request.getTempId())) {
            return List.of();
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

    private void deleteTempState(String tempId) {
        if (StringUtils.hasText(tempId)) {
            redisTemplate.delete(tempKey(tempId));
        }
    }

    private String resolveReportName(OutlineConfirmRequest request) {
        if (StringUtils.hasText(request.getName())) {
            return request.getName().strip();
        }
        return request.getSubject().strip() + "报告";
    }

    private String resolveReportName(OutlineDraftRequest request) {
        if (StringUtils.hasText(request.getName())) {
            return request.getName().strip();
        }
        return request.getSubject().strip() + "报告";
    }

    private ReportEntity requireReport(String reportId) {
        if (!StringUtils.hasText(reportId)) {
            throw new IllegalArgumentException("reportId 不能为空");
        }
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null || Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("报告不存在或已删除: " + reportId);
        }
        return report;
    }

    private void ensureDraftStatus(ReportEntity report) {
        if (!ReportStatus.DRAFT.name().equals(report.getStatus())) {
            throw new IllegalStateException("只有草稿状态的大纲可以继续保存或确认");
        }
    }

    private void applyDraftMetadata(ReportEntity report, OutlineDraftRequest request, int outlineCount) {
        report.setName(resolveReportName(request));
        report.setReportType(request.getReportType().name());
        report.setSubject(request.getSubject());
        report.setSpecialty(request.getSpecialty());
        report.setPowerPlant(request.getPowerPlant());
        report.setReportYear(request.getReportYear());
        report.setStatus(ReportStatus.DRAFT.name());
        report.setTotalSections(outlineCount);
    }

    private void replaceOutlineNodes(String reportId, List<OutlineNodeResponse> outline) {
        deleteOutlineNodes(reportId);
        saveOutlineNodes(reportId, null, outline);
    }

    private void deleteOutlineNodes(String reportId) {
        List<ReportOutlineNodeEntity> nodes = outlineNodeMapper.selectList(
                new LambdaQueryWrapper<ReportOutlineNodeEntity>()
                        .eq(ReportOutlineNodeEntity::getReportId, reportId)
        );
        nodes.stream()
                .sorted(Comparator
                        .comparing(ReportOutlineNodeEntity::getLevel, Comparator.nullsFirst(Integer::compareTo))
                        .reversed())
                .forEach(node -> outlineNodeMapper.deleteById(node.getId()));
    }

    private List<OutlineNodeResponse> loadOutlineTree(String reportId) {
        List<ReportOutlineNodeEntity> nodes = outlineNodeMapper.selectList(
                new LambdaQueryWrapper<ReportOutlineNodeEntity>()
                        .eq(ReportOutlineNodeEntity::getReportId, reportId)
        );
        Map<String, List<ReportOutlineNodeEntity>> childrenByParent = new LinkedHashMap<>();
        for (ReportOutlineNodeEntity node : nodes) {
            childrenByParent
                    .computeIfAbsent(parentKey(node.getParentId()), ignored -> new ArrayList<>())
                    .add(node);
        }
        childrenByParent.values().forEach(list -> list.sort(this::compareOutlineNode));
        return toOutlineTree(childrenByParent, ROOT_PARENT_KEY);
    }

    private List<OutlineNodeResponse> toOutlineTree(
            Map<String, List<ReportOutlineNodeEntity>> childrenByParent,
            String parentKey
    ) {
        List<OutlineNodeResponse> result = new ArrayList<>();
        for (ReportOutlineNodeEntity entity : childrenByParent.getOrDefault(parentKey, List.of())) {
            OutlineNodeResponse node = new OutlineNodeResponse();
            node.setId(entity.getId());
            node.setNumber(entity.getNumber());
            node.setTitle(entity.getTitle());
            node.setLevel(entity.getLevel());
            node.setPromptHint(visiblePromptHint(entity.getPromptHint()));
            node.setTables(readTablePlans(tableJsonFromPromptHint(entity.getPromptHint())));
            node.setChildren(toOutlineTree(childrenByParent, entity.getId()));
            result.add(node);
        }
        return result;
    }

    private String parentKey(String parentId) {
        return StringUtils.hasText(parentId) ? parentId : ROOT_PARENT_KEY;
    }

    private int compareOutlineNode(ReportOutlineNodeEntity left, ReportOutlineNodeEntity right) {
        int sortCompare = Comparator
                .comparing(ReportOutlineNodeEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .compare(left, right);
        if (sortCompare != 0) {
            return sortCompare;
        }
        return compareNumber(left.getNumber(), right.getNumber());
    }

    private int compareNumber(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private OutlineDraftResponse toDraftResponse(ReportEntity report, List<OutlineNodeResponse> outline) {
        OutlineDraftResponse response = new OutlineDraftResponse();
        response.setReportId(report.getId());
        response.setStatus(report.getStatus());
        response.setName(report.getName());
        response.setReportType(report.getReportType());
        response.setSubject(report.getSubject());
        response.setSpecialty(report.getSpecialty());
        response.setPowerPlant(report.getPowerPlant());
        response.setReportYear(report.getReportYear());
        response.setOutline(outline);
        response.setOutlineCount(countOutlineNodes(outline));
        return response;
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
            String tableJson = writeTablePlans(safeTables(node));
            entity.setPromptHint(promptHintWithTableJson(node.getPromptHint(), tableJson));
            entity.setTableJson(tableJson);
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
        normalized.setPromptHint(visiblePromptHint(node.getPromptHint()));
        List<OutlineTablePlan> tables = normalizeTablePlans(node.getTables());
        if (tables.isEmpty()) {
            tables = readTablePlans(tableJsonFromPromptHint(node.getPromptHint()));
        }
        normalized.setTables(tables);

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

    private List<OutlineTablePlan> safeTables(OutlineNodeResponse node) {
        return node.getTables() == null ? List.of() : node.getTables();
    }

    private String promptHintWithTableJson(String promptHint, String tableJson) {
        String visiblePromptHint = visiblePromptHint(promptHint);
        if (!StringUtils.hasText(tableJson)) {
            return visiblePromptHint;
        }
        String encoded = Base64.getEncoder().encodeToString(tableJson.getBytes(StandardCharsets.UTF_8));
        String marker = TABLE_PLAN_PREFIX + encoded + TABLE_PLAN_SUFFIX;
        return StringUtils.hasText(visiblePromptHint)
                ? visiblePromptHint.stripTrailing() + "\n" + marker
                : marker;
    }

    private String visiblePromptHint(String promptHint) {
        if (!StringUtils.hasText(promptHint)) {
            return promptHint;
        }
        int start = promptHint.indexOf(TABLE_PLAN_PREFIX);
        if (start < 0) {
            return promptHint;
        }
        int end = promptHint.indexOf(TABLE_PLAN_SUFFIX, start);
        String before = promptHint.substring(0, start).stripTrailing();
        String after = end >= 0
                ? promptHint.substring(end + TABLE_PLAN_SUFFIX.length()).stripLeading()
                : "";
        String visible = StringUtils.hasText(after) ? before + "\n" + after : before;
        return StringUtils.hasText(visible) ? visible.strip() : null;
    }

    private String tableJsonFromPromptHint(String promptHint) {
        if (!StringUtils.hasText(promptHint)) {
            return null;
        }
        int start = promptHint.indexOf(TABLE_PLAN_PREFIX);
        if (start < 0) {
            return null;
        }
        int valueStart = start + TABLE_PLAN_PREFIX.length();
        int end = promptHint.indexOf(TABLE_PLAN_SUFFIX, valueStart);
        if (end <= valueStart) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(promptHint.substring(valueStart, end));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid outline table plan marker ignored. reason={}", ex.getMessage());
            return null;
        }
    }

    private List<OutlineTablePlan> normalizeTablePlans(List<OutlineTablePlan> tables) {
        List<OutlineTablePlan> normalized = new ArrayList<>();
        if (tables == null) {
            return normalized;
        }
        for (OutlineTablePlan table : tables) {
            if (table == null || !StringUtils.hasText(table.getCaption())) {
                continue;
            }
            OutlineTablePlan normalizedTable = new OutlineTablePlan();
            normalizedTable.setId(StringUtils.hasText(table.getId()) ? table.getId() : UUID.randomUUID().toString());
            normalizedTable.setCaption(table.getCaption().strip());
            normalizedTable.setDescription(table.getDescription());
            normalizedTable.setColumns(normalizeColumns(table.getColumns()));
            normalized.add(normalizedTable);
        }
        return normalized;
    }

    private List<String> normalizeColumns(List<String> columns) {
        if (columns == null) {
            return List.of();
        }
        return columns.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .distinct()
                .toList();
    }

    private String writeTablePlans(List<OutlineTablePlan> tables) {
        List<OutlineTablePlan> normalized = normalizeTablePlans(tables);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("表格计划序列化失败", ex);
        }
    }

    private List<OutlineTablePlan> readTablePlans(String tableJson) {
        if (!StringUtils.hasText(tableJson)) {
            return new ArrayList<>();
        }
        try {
            JsonNode node = objectMapper.readTree(tableJson);
            return parseTablePlans(node);
        } catch (JsonProcessingException ex) {
            log.warn("Invalid outline table_json ignored. reason={}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private List<OutlineTablePlan> parseTablePlans(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return new ArrayList<>();
        }
        if (node.isTextual()) {
            try {
                return parseTablePlans(objectMapper.readTree(node.asText()));
            } catch (JsonProcessingException ex) {
                return new ArrayList<>();
            }
        }
        if (node.isObject() && node.has("tables")) {
            return parseTablePlans(node.get("tables"));
        }

        List<OutlineTablePlan> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                toTablePlan(item).ifPresent(result::add);
            }
            return normalizeTablePlans(result);
        }
        toTablePlan(node).ifPresent(result::add);
        return normalizeTablePlans(result);
    }

    private Optional<OutlineTablePlan> toTablePlan(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        String caption = firstText(node, "caption", "name", "title", "tableName");
        if (!StringUtils.hasText(caption)) {
            return Optional.empty();
        }
        OutlineTablePlan table = new OutlineTablePlan();
        table.setId(firstText(node, "id", "tableId"));
        table.setCaption(caption);
        table.setDescription(firstText(node, "description", "note", "promptHint"));
        table.setColumns(readColumns(firstNode(node, "columns", "headers", "fields")));
        return Optional.of(table);
    }

    private List<String> readColumns(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    columns.add(item.asText());
                } else if (item.isObject()) {
                    String name = firstText(item, "name", "title", "label", "field");
                    if (StringUtils.hasText(name)) {
                        columns.add(name);
                    }
                }
            }
        } else if (node.isTextual()) {
            for (String value : node.asText().split("[,，、|]")) {
                columns.add(value);
            }
        }
        return normalizeColumns(columns);
    }

    private List<OutlineNodeResponse> summerPeakCheckOutline() {
        return List.of(
                node("1", "检查概况", 1, List.of(
                        node("1.1", "检查背景", 2),
                        node("1.2", "检查范围", 2),
                        node("1.3", "检查依据", 2)
                )),
                node("2", "设备运行与安全保障情况", 1, List.of(
                        nodeWithTables("2.1", "主设备运行情况", 2,
                                table("主设备运行情况检查表", "设备名称", "检查内容", "检查结果", "备注")),
                        nodeWithTables("2.2", "电气设备检查情况", 2,
                                table("电气设备检查情况表", "设备/系统", "检查项目", "检查结果", "整改要求")),
                        node("2.3", "热控及保护系统检查情况", 2)
                )),
                node("3", "迎峰度夏重点措施落实情况", 1, List.of(
                        node("3.1", "负荷预测与运行安排", 2),
                        nodeWithTables("3.2", "防汛防高温措施", 2,
                                table("防汛防高温措施落实表", "措施类别", "检查内容", "落实情况", "责任部门")),
                        node("3.3", "应急预案与值班安排", 2)
                )),
                node("4", "存在问题", 1, List.of(
                        nodeWithTables("4.1", "设备隐患", 2,
                                table("设备隐患清单", "隐患描述", "风险等级", "责任部门", "整改时限")),
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
                        nodeWithTables("2.1", "库存台账情况", 2,
                                table("煤炭库存台账核对表", "煤种", "账面库存", "实测库存", "差异", "备注")),
                        node("2.2", "入厂煤管理情况", 2),
                        nodeWithTables("2.3", "耗用与盘点情况", 2,
                                table("耗用与盘点情况表", "日期", "入库量", "耗用量", "库存量", "盘点结论"))
                )),
                node("3", "审计发现", 1, List.of(
                        node("3.1", "账实一致性问题", 2),
                        node("3.2", "计量与验收问题", 2),
                        node("3.3", "内控管理问题", 2)
                )),
                node("4", "数据分析", 1, List.of(
                        nodeWithTables("4.1", "库存变化分析", 2,
                                table("库存变化分析表", "月份", "期初库存", "入库量", "耗用量", "期末库存")),
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

    private OutlineNodeResponse nodeWithTables(
            String number,
            String title,
            int level,
            OutlineTablePlan... tables
    ) {
        OutlineNodeResponse response = node(number, title, level, List.of());
        response.setTables(normalizeTablePlans(List.of(tables)));
        return response;
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

    private OutlineTablePlan table(String caption, String... columns) {
        OutlineTablePlan table = new OutlineTablePlan();
        table.setId(UUID.randomUUID().toString());
        table.setCaption(caption);
        table.setColumns(List.of(columns));
        return table;
    }
}
