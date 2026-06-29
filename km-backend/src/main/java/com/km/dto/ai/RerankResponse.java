package com.km.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class RerankResponse {
    private List<RerankItem> items;

    @Data
    public static class RerankItem {
        private Integer index;
        private Float score;
    }
}
