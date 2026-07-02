package com.km.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文本嵌入请求 DTO，对应 km-ai-service 的 /internal/embed。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedRequest {

    private List<String> texts;
    private String model;
}
