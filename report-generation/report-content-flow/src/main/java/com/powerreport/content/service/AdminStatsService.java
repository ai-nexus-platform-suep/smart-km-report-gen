package com.powerreport.content.service;

import com.powerreport.content.dto.AdminStatsOverviewResponse;
import com.powerreport.content.dto.AdminStatsTrendResponse;
import java.util.List;

public interface AdminStatsService {

    AdminStatsOverviewResponse overview();

    List<AdminStatsTrendResponse> trend(Integer days);
}
