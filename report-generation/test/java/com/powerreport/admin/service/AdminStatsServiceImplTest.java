package com.powerreport.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
 
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powerreport.admin.dto.AdminAlertResponse;
import com.powerreport.admin.dto.AdminDistributionItemResponse;
import com.powerreport.admin.dto.AdminHealthMetricResponse;
import com.powerreport.admin.dto.AdminRecentTaskResponse;
import com.powerreport.admin.dto.AdminStatsDistributionResponse;
import com.powerreport.admin.dto.AdminStatsOverviewResponse;
import com.powerreport.admin.dto.AdminStatsTrendResponse;
import com.powerreport.admin.service.serviceImpl.AdminStatsServiceImpl;
import com.powerreport.entity.ReportEntity;
import com.powerreport.enums.ReportStatus;
import com.powerreport.enums.ReportType;
import com.powerreport.enums.SectionStatus;
import com.powerreport.mapper.ReportMapper;
import com.powerreport.mapper.ReportSectionMapper;
import com.powerreport.mapper.ReportTemplateMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceImplTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportTemplateMapper templateMapper;

    @Mock
    private ReportSectionMapper sectionMapper;

    private AdminStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsServiceImpl(reportMapper, templateMapper, sectionMapper);
    }

    @Test
    void overviewCountsEnabledTemplatesReportsOwnersAndSections() {
        when(templateMapper.selectCount(any())).thenReturn(2L);
        when(reportMapper.selectCount(any())).thenReturn(3L);
        when(reportMapper.selectObjs(any())).thenReturn(List.of(4L));
        when(sectionMapper.selectCount(any())).thenReturn(8L);

        AdminStatsOverviewResponse response = service.overview();

        assertThat(response.getTemplateCount()).isEqualTo(2L);
        assertThat(response.getReportCount()).isEqualTo(3L);
        assertThat(response.getUserCount()).isEqualTo(4L);
        assertThat(response.getSectionCount()).isEqualTo(8L);
    }

    @Test
    void trendFillsMissingDatesAndRejectsInvalidDays() {
        LocalDate today = LocalDate.now();
        when(reportMapper.selectMaps(any())).thenReturn(List.of(
                Map.of("date", today.minusDays(1).toString(), "count", 7L)
        ));

        List<AdminStatsTrendResponse> trend = service.trend(3);

        assertThat(trend).hasSize(3);
        assertThat(trend.get(0).getDate()).isEqualTo(today.minusDays(2).toString());
        assertThat(trend.get(0).getCount()).isZero();
        assertThat(trend.get(1).getDate()).isEqualTo(today.minusDays(1).toString());
        assertThat(trend.get(1).getCount()).isEqualTo(7L);
        assertThat(trend.get(2).getDate()).isEqualTo(today.toString());
        assertThat(trend.get(2).getCount()).isZero();

        assertThatThrownBy(() -> service.trend(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days");
    }

    @Test
    void distributionIncludesEveryEnumValueWithZeroDefaults() {
        when(reportMapper.selectMaps(any()))
                .thenReturn(List.of(Map.of("code", ReportType.SUMMER_PEAK_CHECK.name(), "count", 4L)))
                .thenReturn(List.of(Map.of("code", ReportStatus.FAILED.name(), "count", 2L)));

        AdminStatsDistributionResponse response = service.distribution();

        assertThat(response.getReportType())
                .extracting(AdminDistributionItemResponse::getCode)
                .containsExactly(ReportType.SUMMER_PEAK_CHECK.name(), ReportType.COAL_INVENTORY_AUDIT.name());
        assertThat(response.getReportType())
                .filteredOn(item -> item.getCode().equals(ReportType.SUMMER_PEAK_CHECK.name()))
                .singleElement()
                .extracting(AdminDistributionItemResponse::getCount)
                .isEqualTo(4L);
        assertThat(response.getReportType())
                .filteredOn(item -> item.getCode().equals(ReportType.COAL_INVENTORY_AUDIT.name()))
                .singleElement()
                .extracting(AdminDistributionItemResponse::getCount)
                .isEqualTo(0L);
        assertThat(response.getReportStatus())
                .extracting(AdminDistributionItemResponse::getCode)
                .contains(ReportStatus.FAILED.name(), ReportStatus.CONTENT_READY.name());
        assertThat(response.getReportStatus())
                .filteredOn(item -> item.getCode().equals(ReportStatus.FAILED.name()))
                .singleElement()
                .extracting(AdminDistributionItemResponse::getCount)
                .isEqualTo(2L);
    }

    @Test
    void recentTasksCapsLimitAndMapsReports() {
        ReportEntity report = report("report-1", ReportStatus.CONTENT_READY);
        when(reportMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ReportEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(report));
            return page;
        });

        List<AdminRecentTaskResponse> response = service.recentTasks(500);

        ArgumentCaptor<Page<ReportEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        org.mockito.Mockito.verify(reportMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(50);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getReportId()).isEqualTo("report-1");
        assertThat(response.get(0).getStatus()).isEqualTo(ReportStatus.CONTENT_READY.name());
        assertThatThrownBy(() -> service.recentTasks(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void healthAndAlertsReflectCurrentCounts() {
        when(reportMapper.selectCount(any()))
                .thenReturn(10L)
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L)
                .thenReturn(4L)
                .thenReturn(1L)
                .thenReturn(3L);
        when(sectionMapper.selectCount(any()))
                .thenReturn(5L)
                .thenReturn(2L);

        List<AdminHealthMetricResponse> metrics = service.health();
        List<AdminAlertResponse> alerts = service.alerts();

        assertThat(metrics)
                .filteredOn(metric -> metric.getMetric().equals("reportReadyRate"))
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.getValue()).isEqualTo(40L);
                    assertThat(metric.getStatus()).isEqualTo("WARN");
                });
        assertThat(metrics)
                .filteredOn(metric -> metric.getMetric().equals("failedSections"))
                .singleElement()
                .extracting(AdminHealthMetricResponse::getValue)
                .isEqualTo(5L);
        assertThat(alerts)
                .extracting(AdminAlertResponse::getType)
                .containsExactly("REPORT_FAILED", "CONTENT_INCOMPLETE", "SECTION_FAILED");
        assertThat(alerts)
                .filteredOn(alert -> alert.getType().equals("SECTION_FAILED"))
                .singleElement()
                .extracting(AdminAlertResponse::getCount)
                .isEqualTo(2L);
    }

    private ReportEntity report(String id, ReportStatus status) {
        ReportEntity report = new ReportEntity();
        report.setId(id);
        report.setName("Report " + id);
        report.setReportType(ReportType.SUMMER_PEAK_CHECK.name());
        report.setSubject("Safety");
        report.setPowerPlant("Plant A");
        report.setReportYear(2026);
        report.setStatus(status.name());
        report.setTotalSections(5);
        report.setCompletedSections(4);
        report.setDeleted(false);
        report.setCreatedAt(LocalDateTime.now().minusDays(1));
        report.setUpdatedAt(LocalDateTime.now());
        return report;
    }
}
