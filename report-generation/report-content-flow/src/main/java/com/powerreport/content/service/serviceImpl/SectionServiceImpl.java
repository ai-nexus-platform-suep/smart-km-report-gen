package com.powerreport.content.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.config.ReportAiProperties;
import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.SectionService;
import com.powerreport.dto.OutlineTablePlan;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.enums.ContentGenerationMode;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.SectionStatus;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportOutlineNodeMapper;
import com.powerreport.mapper.ReportSectionMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class SectionServiceImpl implements SectionService {

    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_TEMPLATE = "TEMPLATE";
    private static final String SOURCE_REGENERATED = "REGENERATED";
    private static final String SOURCE_USER_EDITED = "USER_EDITED";
    private static final String REGENERATE_HINT_KEY_PREFIX = "report:section:regenerate:";
    private static final String TABLE_PLAN_PREFIX = "<!--TABLE_PLAN_JSON_BASE64:";
    private static final String TABLE_PLAN_SUFFIX = "-->";
    private static final int AI_SECTION_MAX_ATTEMPTS = 3;

    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportSectionMapper sectionMapper;
    private final ReportAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Map<String, String> localRegenerateHints = new ConcurrentHashMap<>();

    @Value("${app.section.generation-state-ttl-seconds:1800}")
    private long generationStateTtlSeconds;

    @Override
    @Transactional
    public SectionGenerateResponse startGeneration(String reportId, SectionGenerateRequest request) {
        SectionGenerateRequest actualRequest = request == null ? new SectionGenerateRequest() : request;
        if (actualRequest.getGenerationMode() == ContentGenerationMode.TEMPLATE) {
            return generateTemplateSections(reportId);
        }

        ReportEntity report = findReport(reportId);
        List<ReportSectionEntity> sections = ensureSectionRows(report);
        int targetCount = 0;

        for (ReportSectionEntity section : sections) {
            if (shouldStartGeneration(section)) {
                section.setStatus(SectionStatus.GENERATING.name());
                section.setSource(SOURCE_AI);
                section.setErrorMessage(null);
                sectionMapper.updateById(section);
                targetCount++;
            }
        }

        report.setStatus(ReportStatus.CONTENT_GENERATING.name());
        report.setTotalSections(sections.size());
        report.setCompletedSections(countCompleted(reportId));
        reportMapper.updateById(report);

        return new SectionGenerateResponse(
                UUID.randomUUID().toString(),
                reportId,
                null,
                ReportStatus.CONTENT_GENERATING.name(),
                sections.size(),
                report.getCompletedSections(),
                targetCount == 0
                        ? "No pending sections. Existing content can be replayed through SSE."
                        : "Section generation task created. Connect to the SSE endpoint to receive AI content."
        );
    }

    private SectionGenerateResponse generateTemplateSections(String reportId) {
        ReportEntity report = findReport(reportId);
        List<ReportSectionEntity> sections = ensureSectionRows(report);
        int generatedCount = 0;

        for (ReportSectionEntity section : sections) {
            if (SectionStatus.USER_EDITED.name().equals(section.getStatus())
                    && StringUtils.hasText(section.getContentMarkdown())) {
                continue;
            }
            section.setContentMarkdown(buildTemplateContent(report, section));
            section.setStatus(SectionStatus.GENERATED.name());
            section.setSource(SOURCE_TEMPLATE);
            section.setVersion(nextVersion(section));
            section.setErrorMessage(null);
            sectionMapper.updateById(section);
            generatedCount++;
        }

        refreshReportProgress(reportId);
        ReportEntity refreshed = findReport(reportId);
        return new SectionGenerateResponse(
                UUID.randomUUID().toString(),
                reportId,
                null,
                refreshed.getStatus(),
                sections.size(),
                refreshed.getCompletedSections(),
                "Template section content created. Generated sections: " + generatedCount
        );
    }

    @Override
    public SseEmitter streamSections(String reportId) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> emitSections(reportId, emitter));
        return emitter;
    }

    @Override
    @Transactional
    public SectionResponse saveSection(String reportId, String sectionId, SectionContentRequest request) {
        ReportSectionEntity section = findSection(reportId, sectionId);
        section.setContentMarkdown(request.getContentMarkdown());
        if (request.getTableJson() != null) {
            section.setTableJson(normalizeTableJson(request.getTableJson()));
        }
        section.setStatus(SectionStatus.USER_EDITED.name());
        section.setSource(SOURCE_USER_EDITED);
        section.setVersion(nextVersion(section));
        section.setErrorMessage(null);
        sectionMapper.updateById(section);
        refreshReportProgress(reportId);
        return toSectionResponse(section);
    }

    @Override
    public SectionResponse getSection(String reportId, String sectionId) {
        return toSectionResponse(findSection(reportId, sectionId));
    }

    @Override
    public List<SectionResponse> listSections(String reportId) {
        findReport(reportId);
        return listSectionEntities(reportId)
                .stream()
                .map(this::toSectionResponse)
                .toList();
    }

    @Override
    @Transactional
    public SectionGenerateResponse regenerateSection(
            String reportId,
            String sectionId,
            SectionRegenerateRequest request
    ) {
        ReportSectionEntity section = findSection(reportId, sectionId);
        section.setStatus(SectionStatus.GENERATING.name());
        section.setSource(SOURCE_REGENERATED);
        section.setErrorMessage(null);
        sectionMapper.updateById(section);
        storeRegenerateHint(reportId, sectionId, request == null ? null : request.getHint());

        ReportEntity report = findReport(reportId);
        report.setStatus(ReportStatus.CONTENT_GENERATING.name());
        report.setCompletedSections(countCompleted(reportId));
        reportMapper.updateById(report);

        return new SectionGenerateResponse(
                UUID.randomUUID().toString(),
                reportId,
                sectionId,
                SectionStatus.GENERATING.name(),
                null,
                report.getCompletedSections(),
                "Section regeneration task created. Connect to the SSE endpoint to receive AI content."
        );
    }

    private void emitSections(String reportId, SseEmitter emitter) {
        try {
            ReportEntity report = findReport(reportId);
            ensureSectionRows(report);
            List<ReportSectionEntity> sections = listSectionEntities(reportId);
            Set<String> targetIds = selectGenerationTargets(sections);
            List<ReportOutlineNodeEntity> outlineNodes = listOutlineNodes(reportId);
            Map<String, ReportOutlineNodeEntity> outlineById = outlineNodes.stream()
                    .collect(Collectors.toMap(
                            ReportOutlineNodeEntity::getId,
                            Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            String outlineContext = buildOutlineContext(outlineNodes);

            int total = sections.size();
            for (int i = 0; i < total; i++) {
                ReportSectionEntity section = sections.get(i);
                sendProgress(emitter, section, i + 1, total);

                if (targetIds.contains(section.getId())) {
                    generateOneSection(report, section, outlineById.get(section.getOutlineNodeId()), outlineContext, emitter);
                } else if (StringUtils.hasText(section.getContentMarkdown())) {
                    sendEvent(emitter, "content", section.getContentMarkdown());
                }

                sendEvent(emitter, "section_done", "[SECTION_DONE]");
            }

            refreshReportProgress(reportId);
            sendEvent(emitter, "done", "[DONE]");
            emitter.complete();
        } catch (RuntimeException ex) {
            sendEvent(emitter, "error", ex.getMessage());
            emitter.completeWithError(ex);
        }
    }

    private void generateOneSection(
            ReportEntity report,
            ReportSectionEntity section,
            ReportOutlineNodeEntity outlineNode,
            String outlineContext,
            SseEmitter emitter
    ) {
        boolean regenerate = SOURCE_REGENERATED.equals(section.getSource());
        String userHint = regenerate ? readRegenerateHint(report.getId(), section.getId()) : null;
        StringBuilder content = new StringBuilder();
        boolean incrementVersion = shouldIncrementVersion(section);

        try {
            String outlineTableJson = outlineNodeTableJson(outlineNode);
            if (!StringUtils.hasText(section.getTableJson()) && StringUtils.hasText(outlineTableJson)) {
                section.setTableJson(outlineTableJson);
            }
            streamAiContentWithRetry(report, section, outlineNode, outlineContext, regenerate, userHint, emitter, content);
            section.setContentMarkdown(sanitizeTables(content.toString(), section.getTableJson()));
            section.setStatus(SectionStatus.GENERATED.name());
            section.setSource(regenerate ? SOURCE_REGENERATED : SOURCE_AI);
            section.setVersion(incrementVersion ? nextVersion(section) : defaultVersion(section));
            section.setErrorMessage(null);
            sectionMapper.updateById(section);
            clearRegenerateHint(report.getId(), section.getId());
            refreshReportProgress(report.getId());
        } catch (RuntimeException ex) {
            section.setStatus(SectionStatus.FAILED.name());
            section.setErrorMessage(limit(ex.getMessage(), 2000));
            sectionMapper.updateById(section);
            clearRegenerateHint(report.getId(), section.getId());
            refreshReportProgress(report.getId());
            sendEvent(emitter, "error", Map.of(
                    "sectionId", section.getId(),
                    "sectionTitle", section.getTitle(),
                    "message", ex.getMessage() == null ? "AI section generation failed" : ex.getMessage()
            ));
        }
    }

    private void streamAiContent(
            ReportEntity report,
            ReportSectionEntity section,
            ReportOutlineNodeEntity outlineNode,
            String outlineContext,
            boolean regenerate,
            String userHint,
            SseEmitter emitter,
            StringBuilder content
    ) {
        if (!StringUtils.hasText(aiProperties.getSectionStreamUrl())) {
            throw new IllegalStateException("app.ai.section-stream-url is not configured");
        }

        String requestBody = toJson(buildAiPayload(report, section, outlineNode, outlineContext, regenerate, userHint));
        log.info("Calling AI section stream: url={}, section={}, bodyLength={}",
                aiProperties.getSectionStreamUrl(), section.getNumber(), requestBody.length());

        HttpRequest request = HttpRequest.newBuilder(URI.create(aiProperties.getSectionStreamUrl()))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(Math.max(1, aiProperties.getTimeoutSeconds())))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "AI section stream returned HTTP " + response.statusCode() + ": " + readLimited(reader)
                    );
                }
                parseAiSse(reader, emitter, content, section.getTableJson());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("AI section stream I/O failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI section stream was interrupted", ex);
        }
    }

    private void streamAiContentWithRetry(
            ReportEntity report,
            ReportSectionEntity section,
            ReportOutlineNodeEntity outlineNode,
            String outlineContext,
            boolean regenerate,
            String userHint,
            SseEmitter emitter,
            StringBuilder content
    ) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= AI_SECTION_MAX_ATTEMPTS; attempt++) {
            int beforeLength = content.length();
            try {
                streamAiContent(report, section, outlineNode, outlineContext, regenerate, userHint, emitter, content);
                String currentAttemptContent = content.substring(beforeLength);
                if (!StringUtils.hasText(currentAttemptContent)) {
                    throw new IllegalStateException("AI section stream returned empty content");
                }
                if (attempt > 1) {
                    log.info("AI section stream recovered after retry. section={}, attempt={}", section.getNumber(), attempt);
                }
                return;
            } catch (RuntimeException ex) {
                lastException = ex;
                if (content.length() > beforeLength) {
                    throw ex;
                }
                if (attempt >= AI_SECTION_MAX_ATTEMPTS) {
                    break;
                }
                log.warn("AI section stream failed before receiving content, retrying. section={}, attempt={}, reason={}",
                        section.getNumber(), attempt, ex.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException(
                "AI section stream failed after " + AI_SECTION_MAX_ATTEMPTS + " attempts: "
                        + (lastException == null ? "unknown error" : lastException.getMessage()),
                lastException
        );
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.min(2000L, 400L * attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI section retry was interrupted", ex);
        }
    }

    private Map<String, Object> buildAiPayload(
            ReportEntity report,
            ReportSectionEntity section,
            ReportOutlineNodeEntity outlineNode,
            String outlineContext,
            boolean regenerate,
            String userHint
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", defaultText(report.getReportType(), "SUMMER_PEAK_CHECK"));
        payload.put("subject", defaultText(report.getSubject(), ""));
        payload.put("powerPlant", defaultText(report.getPowerPlant(), ""));
        payload.put("specialty", defaultText(report.getSpecialty(), ""));
        payload.put("reportYear", report.getReportYear());
        payload.put("sectionNumber", defaultText(section.getNumber(), "1"));
        payload.put("sectionTitle", defaultText(section.getTitle(), "Untitled Section"));
        payload.put("sectionLevel", outlineNode == null || outlineNode.getLevel() == null ? 2 : outlineNode.getLevel());
        payload.put("promptHint", outlineNode == null ? "" : defaultText(visiblePromptHint(outlineNode.getPromptHint()), ""));
        payload.put("outlineContext", defaultText(outlineContext, ""));
        List<OutlineTablePlan> tablePlans = readTablePlans(section.getTableJson());
        payload.put("allowTables", !tablePlans.isEmpty());
        payload.put("tablePlans", tablePlans);
        payload.put("existingContentMarkdown", section.getContentMarkdown());
        payload.put("userHint", userHint);
        payload.put("regenerate", regenerate);
        return payload;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void parseAiSse(
            BufferedReader reader,
            SseEmitter emitter,
            StringBuilder content,
            String tableJson
    ) throws IOException {
        String eventName = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (handleAiEvent(eventName, data.toString(), emitter, content, tableJson)) {
                    return;
                }
                eventName = "message";
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                appendSseData(data, line.substring("data:".length()).stripLeading());
            } else if (data.length() > 0) {
                appendSseData(data, line);
            }
        }

        if (data.length() > 0) {
            handleAiEvent(eventName, data.toString(), emitter, content, tableJson);
        }
    }

    private boolean handleAiEvent(
            String eventName,
            String data,
            SseEmitter emitter,
            StringBuilder content,
            String tableJson
    ) throws IOException {
        if ("content".equals(eventName) || "message".equals(eventName)) {
            if (!data.isEmpty()) {
                String filtered = sanitizeTables(data, tableJson);
                if (StringUtils.hasText(filtered)) {
                    content.append(filtered);
                    sendEvent(emitter, "content", filtered);
                }
            }
            return false;
        }
        if ("end".equals(eventName) || "done".equals(eventName)) {
            return true;
        }
        if ("error".equals(eventName)) {
            throw new IllegalStateException(StringUtils.hasText(data) ? data : "AI section stream returned error");
        }
        return false;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ex) {
            log.warn("SSE client send failed, generation will continue. event={}, reason={}", eventName, ex.getMessage());
        }
    }

    private void appendSseData(StringBuilder data, String value) {
        if (data.length() > 0) {
            data.append('\n');
        }
        data.append(value);
    }

    private void sendProgress(
            SseEmitter emitter,
            ReportSectionEntity section,
            int current,
            int total
    ) {
        sendEvent(emitter, "progress", Map.of(
                "current", current,
                "total", total,
                "sectionId", section.getId(),
                "sectionNumber", section.getNumber(),
                "sectionTitle", section.getTitle()
        ));
    }

    private Set<String> selectGenerationTargets(List<ReportSectionEntity> sections) {
        Set<String> generating = sections.stream()
                .filter(section -> SectionStatus.GENERATING.name().equals(section.getStatus()))
                .map(ReportSectionEntity::getId)
                .collect(Collectors.toSet());
        if (!generating.isEmpty()) {
            return generating;
        }
        return sections.stream()
                .filter(section -> !StringUtils.hasText(section.getContentMarkdown())
                        || SectionStatus.PENDING.name().equals(section.getStatus())
                        || SectionStatus.FAILED.name().equals(section.getStatus()))
                .map(ReportSectionEntity::getId)
                .collect(Collectors.toSet());
    }

    private boolean shouldStartGeneration(ReportSectionEntity section) {
        return !StringUtils.hasText(section.getContentMarkdown())
                || SectionStatus.PENDING.name().equals(section.getStatus())
                || SectionStatus.FAILED.name().equals(section.getStatus())
                || SectionStatus.GENERATING.name().equals(section.getStatus());
    }

    private ReportEntity findReport(String reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null || Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("Report does not exist or has been deleted");
        }
        return report;
    }

    private ReportSectionEntity findSection(String reportId, String sectionId) {
        ReportSectionEntity section = sectionMapper.selectOne(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .eq(ReportSectionEntity::getId, sectionId)
        );
        if (section == null) {
            throw new IllegalArgumentException("Section does not exist");
        }
        return section;
    }

    private List<ReportSectionEntity> ensureSectionRows(ReportEntity report) {
        List<ReportOutlineNodeEntity> outlineNodes = listOutlineNodes(report.getId());
        if (outlineNodes.isEmpty()) {
            throw new IllegalStateException("Report outline is empty; cannot generate section content");
        }

        List<ReportSectionEntity> existing = sectionMapper.selectList(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, report.getId())
        );
        Set<String> existingOutlineIds = existing.stream()
                .map(ReportSectionEntity::getOutlineNodeId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, ReportSectionEntity> existingByOutlineId = existing.stream()
                .filter(section -> StringUtils.hasText(section.getOutlineNodeId()))
                .collect(Collectors.toMap(
                        ReportSectionEntity::getOutlineNodeId,
                        Function.identity(),
                        (left, right) -> left
                ));

        for (ReportOutlineNodeEntity node : outlineNodes) {
            String outlineTableJson = outlineNodeTableJson(node);
            if (existingOutlineIds.contains(node.getId())) {
                ReportSectionEntity existingSection = existingByOutlineId.get(node.getId());
                if (existingSection != null
                        && !StringUtils.hasText(existingSection.getTableJson())
                        && StringUtils.hasText(outlineTableJson)) {
                    existingSection.setTableJson(outlineTableJson);
                    sectionMapper.updateById(existingSection);
                }
                continue;
            }
            ReportSectionEntity section = new ReportSectionEntity();
            section.setId(UUID.randomUUID().toString());
            section.setReportId(report.getId());
            section.setOutlineNodeId(node.getId());
            section.setNumber(node.getNumber());
            section.setTitle(node.getTitle());
            section.setContentMarkdown(null);
            section.setTableJson(outlineTableJson);
            section.setStatus(SectionStatus.PENDING.name());
            section.setSource(SOURCE_AI);
            section.setVersion(1);
            sectionMapper.insert(section);
        }

        return listSectionEntities(report.getId());
    }

    private List<ReportSectionEntity> listSectionEntities(String reportId) {
        return sectionMapper.selectList(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .orderByAsc(ReportSectionEntity::getNumber)
        );
    }

    private List<ReportOutlineNodeEntity> listOutlineNodes(String reportId) {
        List<ReportOutlineNodeEntity> nodes = outlineNodeMapper.selectList(
                new LambdaQueryWrapper<ReportOutlineNodeEntity>()
                        .eq(ReportOutlineNodeEntity::getReportId, reportId)
                        .orderByAsc(ReportOutlineNodeEntity::getNumber)
        );
        return new ArrayList<>(nodes);
    }

    private String buildOutlineContext(List<ReportOutlineNodeEntity> outlineNodes) {
        return outlineNodes.stream()
                .sorted(Comparator.comparing(ReportOutlineNodeEntity::getNumber))
                .map(node -> "  ".repeat(Math.max(0, safeLevel(node) - 1))
                        + node.getNumber() + " " + node.getTitle())
                .collect(Collectors.joining("\n"));
    }

    private int safeLevel(ReportOutlineNodeEntity node) {
        return node.getLevel() == null ? 1 : node.getLevel();
    }

    private int countCompleted(String reportId) {
        return Math.toIntExact(sectionMapper.selectCount(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .in(ReportSectionEntity::getStatus,
                                SectionStatus.GENERATED.name(),
                                SectionStatus.USER_EDITED.name())
                        .isNotNull(ReportSectionEntity::getContentMarkdown)
                        .ne(ReportSectionEntity::getContentMarkdown, "")
        ));
    }

    private void refreshReportProgress(String reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null || Boolean.TRUE.equals(report.getDeleted())) {
            return;
        }

        long total = sectionMapper.selectCount(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
        );
        int completed = countCompleted(reportId);
        long failed = sectionMapper.selectCount(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .eq(ReportSectionEntity::getStatus, SectionStatus.FAILED.name())
        );
        long generating = sectionMapper.selectCount(
                new LambdaQueryWrapper<ReportSectionEntity>()
                        .eq(ReportSectionEntity::getReportId, reportId)
                        .eq(ReportSectionEntity::getStatus, SectionStatus.GENERATING.name())
        );

        report.setTotalSections(Math.toIntExact(total));
        report.setCompletedSections(completed);
        if (generating > 0) {
            report.setStatus(ReportStatus.CONTENT_GENERATING.name());
        } else if (total > 0 && completed == total) {
            report.setStatus(ReportStatus.CONTENT_READY.name());
            report.setGeneratedAt(LocalDateTime.now());
        } else if (failed > 0 || total > 0) {
            report.setStatus(ReportStatus.CONTENT_INCOMPLETE.name());
        } else {
            report.setStatus(ReportStatus.OUTLINE_READY.name());
        }
        reportMapper.updateById(report);
    }

    private boolean shouldIncrementVersion(ReportSectionEntity section) {
        return SOURCE_REGENERATED.equals(section.getSource()) || StringUtils.hasText(section.getContentMarkdown());
    }

    private int defaultVersion(ReportSectionEntity section) {
        return section.getVersion() == null ? 1 : section.getVersion();
    }

    private int nextVersion(ReportSectionEntity section) {
        return section.getVersion() == null ? 1 : section.getVersion() + 1;
    }

    private String outlineNodeTableJson(ReportOutlineNodeEntity node) {
        if (node == null) {
            return null;
        }
        if (StringUtils.hasText(node.getTableJson())) {
            return node.getTableJson();
        }
        return tableJsonFromPromptHint(node.getPromptHint());
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

    private String buildTemplateContent(ReportEntity report, ReportSectionEntity section) {
        StringBuilder content = new StringBuilder();
        content.append("本章节依据已确认的大纲模板生成，围绕“")
                .append(defaultText(section.getTitle(), "本章节"))
                .append("”补充报告正文。请结合")
                .append(defaultText(report.getPowerPlant(), "电厂"))
                .append("、")
                .append(defaultText(report.getSpecialty(), "专业"))
                .append("和")
                .append(report.getReportYear() == null ? "报告年度" : report.getReportYear() + "年")
                .append("实际情况完善检查过程、主要结论和整改要求。");

        List<OutlineTablePlan> tablePlans = readTablePlans(section.getTableJson());
        for (OutlineTablePlan table : tablePlans) {
            content.append("\n\n表：").append(table.getCaption()).append("\n");
            List<String> columns = table.getColumns() == null || table.getColumns().isEmpty()
                    ? List.of("项目", "内容", "备注")
                    : table.getColumns();
            content.append("| ").append(String.join(" | ", columns)).append(" |\n");
            content.append("| ").append(columns.stream().map(ignored -> "---").collect(Collectors.joining(" | "))).append(" |\n");
            content.append("| ").append(columns.stream().map(ignored -> "待补充").collect(Collectors.joining(" | "))).append(" |\n");
        }
        return content.toString();
    }

    private String sanitizeTables(String markdown, String tableJson) {
        if (!StringUtils.hasText(markdown) || !readTablePlans(tableJson).isEmpty()) {
            return markdown;
        }

        List<String> lines = new ArrayList<>();
        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String trimmed = line.trim();
            if (isTableCaption(trimmed) || isMarkdownTableLine(trimmed) || isMarkdownSeparatorLine(trimmed)) {
                continue;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    private boolean isTableCaption(String line) {
        return line.startsWith("表：")
                || line.startsWith("表:")
                || line.matches("^表\\s*\\d+(\\.\\d+)?\\s+.*");
    }

    private boolean isMarkdownTableLine(String line) {
        return line.startsWith("|") && line.contains("|");
    }

    private boolean isMarkdownSeparatorLine(String line) {
        String stripped = line.replace("|", "").replace(":", "").replace("-", "").trim();
        return stripped.isEmpty() && line.contains("---");
    }

    private String normalizeTableJson(String tableJson) {
        List<OutlineTablePlan> tablePlans = readTablePlans(tableJson);
        if (tablePlans.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tablePlans);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("tableJson must be valid table plan JSON", ex);
        }
    }

    private List<OutlineTablePlan> readTablePlans(String tableJson) {
        if (!StringUtils.hasText(tableJson)) {
            return new ArrayList<>();
        }
        try {
            return readTablePlans(objectMapper.readTree(tableJson));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("tableJson must be valid JSON", ex);
        }
    }

    private List<OutlineTablePlan> readTablePlans(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return new ArrayList<>();
        }
        if (node.isObject() && node.has("tables")) {
            return readTablePlans(node.get("tables"));
        }

        List<OutlineTablePlan> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                toTablePlan(item).ifPresent(result::add);
            }
        } else {
            toTablePlan(node).ifPresent(result::add);
        }
        return result;
    }

    private java.util.Optional<OutlineTablePlan> toTablePlan(JsonNode node) {
        if (node == null || !node.isObject()) {
            return java.util.Optional.empty();
        }
        String caption = firstText(node, "caption", "name", "title", "tableName");
        if (!StringUtils.hasText(caption)) {
            return java.util.Optional.empty();
        }
        OutlineTablePlan table = new OutlineTablePlan();
        table.setId(firstText(node, "id", "tableId"));
        table.setCaption(caption);
        table.setDescription(firstText(node, "description", "note", "promptHint"));
        table.setColumns(readColumns(firstNode(node, "columns", "headers", "fields")));
        return java.util.Optional.of(table);
    }

    private List<String> readColumns(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    columns.add(item.asText().strip());
                } else if (item.isObject()) {
                    String name = firstText(item, "name", "title", "label", "field");
                    if (StringUtils.hasText(name)) {
                        columns.add(name);
                    }
                }
            }
        } else if (node.isTextual()) {
            for (String value : node.asText().split("[,，、|]")) {
                if (StringUtils.hasText(value)) {
                    columns.add(value.strip());
                }
            }
        }
        return columns.stream().distinct().toList();
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

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().strip();
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AI section request", ex);
        }
    }

    private String readLimited(BufferedReader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null && result.length() < 1000) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(line);
        }
        return result.toString();
    }

    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private void storeRegenerateHint(String reportId, String sectionId, String hint) {
        String key = regenerateHintKey(reportId, sectionId);
        if (!StringUtils.hasText(hint)) {
            clearRegenerateHint(reportId, sectionId);
            return;
        }
        localRegenerateHints.put(key, hint);
        try {
            redisTemplate.opsForValue().set(key, hint, Duration.ofSeconds(Math.max(1, generationStateTtlSeconds)));
        } catch (RuntimeException ignored) {
            // Local fallback keeps single-node development usable when Redis is temporarily unavailable.
        }
    }

    private String readRegenerateHint(String reportId, String sectionId) {
        String key = regenerateHintKey(reportId, sectionId);
        try {
            String redisHint = redisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(redisHint)) {
                return redisHint;
            }
        } catch (RuntimeException ignored) {
            // Fall through to local fallback.
        }
        return localRegenerateHints.get(key);
    }

    private void clearRegenerateHint(String reportId, String sectionId) {
        String key = regenerateHintKey(reportId, sectionId);
        localRegenerateHints.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ignored) {
            // Ignore Redis cleanup failures; TTL will eventually clear the key.
        }
    }

    private String regenerateHintKey(String reportId, String sectionId) {
        return REGENERATE_HINT_KEY_PREFIX + reportId + ":" + sectionId;
    }

    private SectionResponse toSectionResponse(ReportSectionEntity section) {
        SectionResponse response = new SectionResponse();
        response.setSectionId(section.getId());
        response.setOutlineNodeId(section.getOutlineNodeId());
        response.setReportId(section.getReportId());
        response.setNumber(section.getNumber());
        response.setTitle(section.getTitle());
        response.setContentMarkdown(section.getContentMarkdown());
        response.setTableJson(section.getTableJson());
        response.setStatus(section.getStatus());
        response.setSource(section.getSource());
        response.setVersion(section.getVersion());
        response.setErrorMessage(section.getErrorMessage());
        response.setUpdatedAt(section.getUpdatedAt());
        return response;
    }
}
