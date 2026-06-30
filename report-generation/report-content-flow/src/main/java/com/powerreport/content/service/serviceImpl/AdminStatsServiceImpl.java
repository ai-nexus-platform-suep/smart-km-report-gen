package com.powerreport.content.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powerreport.content.dto.AdminStatsOverviewResponse;
import com.powerreport.content.dto.AdminStatsTrendResponse;
import com.powerreport.content.service.AdminStatsService;
import com.powerreport.entity.ReportEntity;
import com.powerreport.entity.ReportSectionEntity;
import com.powerreport.entity.ReportTemplateEntity;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportSectionMapper;
import com.powerreport.mapper.ReportTemplateMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private AdminStatsTrendResponse toTrendResponse(LocalDate date, Long count) {
        AdminStatsTrendResponse response = new AdminStatsTrendResponse();
        response.setDate(date.format(DATE_FORMATTER));
        response.setCount(count);
        return response;
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_TREND_DAYS;
        }
        if (days < 1) {
            throw new IllegalArgumentException("统计天数必须大于 0");
        }
        return Math.min(days, MAX_TREND_DAYS);
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
