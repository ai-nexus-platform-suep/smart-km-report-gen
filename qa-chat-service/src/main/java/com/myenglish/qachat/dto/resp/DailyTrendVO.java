package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DailyTrendVO {

    private LocalDate date;

    private long count;
}
