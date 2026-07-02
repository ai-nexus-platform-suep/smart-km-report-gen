package com.myenglish.qachat.mapper.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DailyCountRow {

    private LocalDate statDate;

    private Long count;
}
