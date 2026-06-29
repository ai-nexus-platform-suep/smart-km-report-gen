package com.powerreport.content.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerreport.config.ReportAiProperties;
import com.powerreport.content.dto.SectionContentRequest;
import com.powerreport.content.dto.SectionGenerateResponse;
import com.powerreport.content.dto.SectionRegenerateRequest;
import com.powerreport.content.dto.SectionResponse;
import com.powerreport.content.service.SectionService;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportOutlineNodeEntity;
import com.powerreport.entity.ReportSectionEntity;
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

    private static final String REPORT_STATUS_GENERATING = "GENERATING";
    private static final String REPORT_STATUS_GENERATED = "GENERATED";
    private static final String REPORT_STATUS_FAILED = "FAILED";
    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_REGENERATED = "REGENERATED";
    private static final String SOURCE_USER_EDITED = "USER_EDITED";
    private static final String REGENERATE_HINT_KEY_PREFIX = "report:section:regenerate:";

    private final ReportMapper reportMapper;
    private final ReportOutlineNodeMapper outlineNodeMapper;
    private final ReportSectionMapper sectionMapper;
    private final ReportAiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final Map<String, String> localRegenerateHints = new ConcurrentHashMap<>();

    @Value("${app.section.generation-state-ttl-seconds:1800}")
    private long generationStateTtlSeconds;

    @Override
    @Transactional
    public SectionGenerateResponse startGeneration(String reportId) {
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

        report.setStatus(REPORT_STATUS_GENERATING);
        report.setTotalSections(sections.size());
        report.setCompletedSections(countCompleted(reportId));
        reportMapper.updateById(report);

        return new SectionGenerateResponse(
                UUID.randomUUID().toString(),
                reportId,
                null,
                REPORT_STATUS_GENERATING,
                sections.size(),
                report.getCompletedSections(),
                targetCount == 0
                        ? "No pending sections. Existing content can be replayed through SSE."
                        : "Section generation task created. Connect to the SSE endpoint to receive AI content."
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
        report.setStatus(REPORT_STATUS_GENERATING);
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
                    emitter.send(SseEmitter.event().name("content").data(section.getContentMarkdown()));
                }

                emitter.send(SseEmitter.event().name("section_done").data("[SECTION_DONE]"));
            }

            refreshReportProgress(reportId);
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (IOException | RuntimeException ex) {
            try {
                emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
            } catch (IOException ignored) {
                // Ignore secondary send failures while closing SSE connection.
            }
            emitter.completeWithError(ex);
        }
    }

    private void generateOneSection(
            ReportEntity report,
            ReportSectionEntity section,
            ReportOutlineNodeEntity outlineNode,
            String outlineContext,
            SseEmitter emitter
    ) throws IOException {
        boolean regenerate = SOURCE_REGENERATED.equals(section.getSource());
        String userHint = regenerate ? readRegenerateHint(report.getId(), section.getId()) : null;
        StringBuilder content = new StringBuilder();
        boolean incrementVersion = shouldIncrementVersion(section);

        try {
            streamAiContent(report, section, outlineNode, outlineContext, regenerate, userHint, emitter, content);
            section.setContentMarkdown(content.toString());
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
            refreshReportProgress(report.getId());
            emitter.send(SseEmitter.event().name("error").data(Map.of(
                    "sectionId", section.getId(),
                    "sectionTitle", section.getTitle(),
                    "message", ex.getMessage() == null ? "AI section generation failed" : ex.getMessage()
            )));
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
                parseAiSse(reader, emitter, content);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("AI section stream I/O failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI section stream was interrupted", ex);
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
        payload.put("promptHint", outlineNode == null ? "" : defaultText(outlineNode.getPromptHint(), ""));
        payload.put("outlineContext", defaultText(outlineContext, ""));
        payload.put("existingContentMarkdown", section.getContentMarkdown());
        payload.put("userHint", userHint);
        payload.put("regenerate", regenerate);
        return payload;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private void parseAiSse(BufferedReader reader, SseEmitter emitter, StringBuilder content) throws IOException {
        String eventName = "message";
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (handleAiEvent(eventName, data.toString(), emitter, content)) {
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
            handleAiEvent(eventName, data.toString(), emitter, content);
        }
    }

    private boolean handleAiEvent(
            String eventName,
            String data,
            SseEmitter emitter,
            StringBuilder content
    ) throws IOException {
        if ("content".equals(eventName) || "message".equals(eventName)) {
            if (!data.isEmpty()) {
                content.append(data);
                emitter.send(SseEmitter.event().name("content").data(data));
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
    ) throws IOException {
        emitter.send(SseEmitter.event()
                .name("progress")
                .data(Map.of(
                        "current", current,
                        "total", total,
                        "sectionId", section.getId(),
                        "sectionNumber", section.getNumber(),
                        "sectionTitle", section.getTitle()
                )));
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

        for (ReportOutlineNodeEntity node : outlineNodes) {
            if (existingOutlineIds.contains(node.getId())) {
                continue;
            }
            ReportSectionEntity section = new ReportSectionEntity();
            section.setId(UUID.randomUUID().toString());
            section.setReportId(report.getId());
            section.setOutlineNodeId(node.getId());
            section.setNumber(node.getNumber());
            section.setTitle(node.getTitle());
            section.setContentMarkdown(null);
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

        report.setTotalSections(Math.toIntExact(total));
        report.setCompletedSections(completed);
        if (total > 0 && completed == total) {
            report.setStatus(REPORT_STATUS_GENERATED);
            report.setGeneratedAt(LocalDateTime.now());
        } else if (failed > 0) {
            report.setStatus(REPORT_STATUS_FAILED);
        } else {
            report.setStatus(REPORT_STATUS_GENERATING);
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
        response.setStatus(section.getStatus());
        response.setSource(section.getSource());
        response.setVersion(section.getVersion());
        response.setErrorMessage(section.getErrorMessage());
        response.setUpdatedAt(section.getUpdatedAt());
        return response;
    }
}
