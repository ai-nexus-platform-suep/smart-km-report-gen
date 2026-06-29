package com.km.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequest {
    private String query;
    private List<String> passages;
    private Integer topK;
    private String model;
}
