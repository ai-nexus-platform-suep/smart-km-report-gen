package com.km.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class SearchResultVO {

    private List<SearchResultItemVO> results;
    private int total;
}
