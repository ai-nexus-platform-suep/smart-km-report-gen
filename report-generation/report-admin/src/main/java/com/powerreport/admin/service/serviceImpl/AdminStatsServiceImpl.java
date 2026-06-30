package com.powerreport.admin.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerreport.admin.dto.AdminAlertResponse;
import com.powerreport.admin.dto.AdminDistributionItemResponse;
import com.powerreport.admin.dto.AdminHealthMetricResponse;
import com.powerreport.admin.dto.AdminRecentTaskResponse;
import com.powerreport.admin.dto.AdminStatsDashboardResponse;
import com.powerreport.admin.dto.AdminStatsDistributionResponse;
import com.powerreport.admin.dto.AdminStatsOverviewResponse;
import com.powerreport.admin.dto.AdminStatsTrendResponse;
import com.powerreport.admin.service.AdminStatsService;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.enums.SectionStatus;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportSectionMapper;
import com.powerreport.mapper.ReportTemplateMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final int DEFAULT_TREND_DAYS = 30;
    private static final int MAX_TREND_DAYS = 90;
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_RECENT_LIMIT = 50;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ReportMapper reportMapper;
    private final ReportTemplateMapper templateMapper;
    private final ReportSectionMapper sectionMapper;

    @Override
    public AdminStatsOverviewResponse overview() {
        AdminStatsOverviewResponse response = new AdminStatsOverviewResponse();
        response.setTemplateCount(countTemplates());
        response.setReportCount(countReports());
        response.setUserCount(countDistinctOwners());
        response.setSectionCount(sectionMapper.selectCount(new LambdaQueryWrapper<ReportSectionEntity>()));
        return response;
    }

    @Override
    public List<AdminStatsTrendResponse> trend(Integer days) {
        int actualDays = normalizeDays(days);
        LocalDate startDate = LocalDate.now().minusDays(actualDays - 1L);
        Map<String, Long> countByDate = selectTrendRows(startDate).stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("date")),
                        row -> toLong(row.get("count"))
                ));

        return startDate.datesUntil(LocalDate.now().plusDays(1L))
                .map(date -> toTrendResponse(date, countByDate.getOrDefault(date.format(DATE_FORMATTER), 0L)))
                .toList();
    }

    @Override
    public AdminStatsDistributionResponse distribution() {
        AdminStatsDistributionResponse response = new AdminStatsDistributionResponse();
        response.setReportType(buildReportTypeDistribution());
        response.setReportStatus(buildReportStatusDistribution());
        return response;
    }

    @Override
    public List<AdminRecentTaskResponse> recentTasks(Integer limit) {
        int actualLimit = normalizeLimit(limit);
        Page<ReportEntity> result = reportMapper.selectPage(
                Page.of(1, actualLimit),
                new LambdaQueryWrapper<ReportEntity>()
                        .eq(ReportEntity::getDeleted, false)
                        .orderByDesc(ReportEntity::getUpdatedAt)
                        .orderByDesc(ReportEntity::getCreatedAt)
        );
        return result.getRecords().stream()
                .map(this::toRecentTask)
                .toList();
    }

    @Override
    public List<AdminHealthMetricResponse> health() {
        long totalReports = countReports();
        long failedReports = countReportsByStatus(ReportStatus.FAILED);
        long generatingReports = countReportsByStatus(ReportStatus.CONTENT_GENERATING);
        long incompleteReports = countReportsByStatus(ReportStatus.CONTENT_INCOMPLETE);
        long readyReports = countReportsByStatuses(List.of(ReportStatus.CONTENT_READY, ReportStatus.EXPORTED));
        long failedSections = countSectionsByStatus(SectionStatus.FAILED.name());
        long readyRate = totalReports == 0 ? 100L : Math.round(readyReports * 100.0 / totalReports);

        List<AdminHealthMetricResponse> metrics = new ArrayList<>();
        metrics.add(toHealthMetric("reportReadyRate", "Report ready rate", readyRate,
                "%", readyRate >= 80 ? "NORMAL" : "WARN"));
        metrics.add(toHealthMetric("failedReports", "Failed reports", failedReports,
                "count", failedReports == 0 ? "NORMAL" : "WARN"));
        metrics.add(toHealthMetric("generatingReports", "Generating reports", generatingReports,
                "count", "NORMAL"));
        metrics.add(toHealthMetric("incompleteReports", "Incomplete reports", incompleteReports,
                "count", incompleteReports == 0 ? "NORMAL" : "WARN"));
        metrics.add(toHealthMetric("failedSections", "Failed sections", failedSections,
                "count", failedSections == 0 ? "NORMAL" : "WARN"));
        return metrics;
    }

    @Override
    public List<AdminAlertResponse> alerts() {
        List<AdminAlertResponse> alerts = new ArrayList<>();
        long failedReports = countReportsByStatus(ReportStatus.FAILED);
        long incompleteReports = countReportsByStatus(ReportStatus.CONTENT_INCOMPLETE);
        long failedSections = countSectionsByStatus(SectionStatus.FAILED.name());

        if (failedReports > 0) {
            alerts.add(toAlert("REPORT_FAILED", "WARN", "Failed report generation tasks exist", failedReports));
        }
        if (incompleteReports > 0) {
            alerts.add(toAlert("CONTENT_INCOMPLETE", "INFO", "Reports waiting for content completion exist", incompleteReports));
        }
        if (failedSections > 0) {
            alerts.add(toAlert("SECTION_FAILED", "WARN", "Failed section generation records exist", failedSections));
        }
        return alerts;
    }

    @Override
    public AdminStatsDashboardResponse dashboard(Integer days, Integer recentLimit) {
        AdminStatsDashboardResponse response = new AdminStatsDashboardResponse();
        response.setOverview(overview());
        response.setTrends(trend(days));
        response.setDistributions(distribution());
        response.setRecentTasks(recentTasks(recentLimit));
        response.setHealth(health());
        response.setAlerts(alerts());
        return response;
    }

    private Long countTemplates() {
        return templateMapper.selectCount(new LambdaQueryWrapper<ReportTemplateEntity>()
                .eq(ReportTemplateEntity::getEnabled, true));
    }

    private Long countReports() {
        return reportMapper.selectCount(new LambdaQueryWrapper<ReportEntity>()
                .eq(ReportEntity::getDeleted, false));
    }

    private Long countDistinctOwners() {
        List<Object> values = reportMapper.selectObjs(new QueryWrapper<ReportEntity>()
                .select("COUNT(DISTINCT owner_name)")
                .eq("deleted", false));
        if (values.isEmpty()) {
            return 0L;
        }
        return toLong(values.get(0));
    }

    private List<Map<String, Object>> selectTrendRows(LocalDate startDate) {
        return reportMapper.selectMaps(new QueryWrapper<ReportEntity>()
                .select("DATE_FORMAT(created_at, '%Y-%m-%d') AS date", "COUNT(*) AS count")
                .eq("deleted", false)
                .ge("created_at", startDate.atStartOfDay())
                .groupBy("DATE_FORMAT(created_at, '%Y-%m-%d')")
                .orderByAsc("date"));
    }

    private List<AdminDistributionItemResponse> buildReportTypeDistribution() {
        Map<String, Long> countByType = selectGroupCounts("type");
        return Arrays.stream(ReportType.values())
                .map(type -> toDistributionItem(type.name(), type.getLabel(), countByType.getOrDefault(type.name(), 0L)))
                .toList();
    }

    private List<AdminDistributionItemResponse> buildReportStatusDistribution() {
        Map<String, Long> countByStatus = selectGroupCounts("status");
        return Arrays.stream(ReportStatus.values())
                .map(status -> toDistributionItem(status.name(), status.getLabel(), countByStatus.getOrDefault(status.name(), 0L)))
                .toList();
    }

    private Map<String, Long> selectGroupCounts(String column) {
        Map<String, Long> result = new LinkedHashMap<>();
        reportMapper.selectMaps(new QueryWrapper<ReportEntity>()
                        .select(column + " AS code", "COUNT(*) AS count")
                        .eq("deleted", false)
                        .groupBy(column))
                .forEach(row -> result.put(String.valueOf(row.get("code")), toLong(row.get("count"))));
        return result;
    }

    private Long countReportsByStatus(ReportStatus status) {
        return reportMapper.selectCount(new LambdaQueryWrapper<ReportEntity>()
                .eq(ReportEntity::getDeleted, false)
                .eq(ReportEntity::getStatus, status.name()));
    }

    private Long countReportsByStatuses(List<ReportStatus> statuses) {
        return reportMapper.selectCount(new LambdaQueryWrapper<ReportEntity>()
                .eq(ReportEntity::getDeleted, false)
                .in(ReportEntity::getStatus, statuses.stream().map(Enum::name).toList()));
    }

    private Long countSectionsByStatus(String status) {
        return sectionMapper.selectCount(new LambdaQueryWrapper<ReportSectionEntity>()
                .eq(ReportSectionEntity::getStatus, status));
    }

    private AdminStatsTrendResponse toTrendResponse(LocalDate date, Long count) {
        AdminStatsTrendResponse response = new AdminStatsTrendResponse();
        response.setDate(date.format(DATE_FORMATTER));
        response.setCount(count);
        return response;
    }

    private AdminDistributionItemResponse toDistributionItem(String code, String label, Long count) {
        AdminDistributionItemResponse response = new AdminDistributionItemResponse();
        response.setCode(code);
        response.setLabel(label);
        response.setCount(count);
        return response;
    }

    private AdminRecentTaskResponse toRecentTask(ReportEntity report) {
        AdminRecentTaskResponse response = new AdminRecentTaskResponse();
        response.setReportId(report.getId());
        response.setName(report.getName());
        response.setType(report.getReportType());
        response.setSubject(report.getSubject());
        response.setPowerPlant(report.getPowerPlant());
        response.setReportYear(report.getReportYear());
        response.setStatus(report.getStatus());
        response.setTotalSections(report.getTotalSections());
        response.setCompletedSections(report.getCompletedSections());
        response.setCreatedAt(report.getCreatedAt());
        response.setUpdatedAt(report.getUpdatedAt());
        return response;
    }

    private AdminHealthMetricResponse toHealthMetric(String metric, String label, Long value, String unit, String status) {
        AdminHealthMetricResponse response = new AdminHealthMetricResponse();
        response.setMetric(metric);
        response.setLabel(label);
        response.setValue(value);
        response.setUnit(unit);
        response.setStatus(status);
        return response;
    }

    private AdminAlertResponse toAlert(String type, String level, String message, Long count) {
        AdminAlertResponse response = new AdminAlertResponse();
        response.setType(type);
        response.setLevel(level);
        response.setMessage(message);
        response.setCount(count);
        return response;
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_TREND_DAYS;
        }
        if (days < 1) {
            throw new IllegalArgumentException("days must be greater than 0");
        }
        return Math.min(days, MAX_TREND_DAYS);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RECENT_LIMIT;
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return Math.min(limit, MAX_RECENT_LIMIT);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
