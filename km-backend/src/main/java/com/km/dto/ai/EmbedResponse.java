package com.km.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class EmbedResponse {
    private List<List<Float>> vectors;
    private Integer dimension;
}
