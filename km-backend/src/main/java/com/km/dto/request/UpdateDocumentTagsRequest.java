package com.km.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 更新文档标签请求
 */
@Data
public class UpdateDocumentTagsRequest {

    @NotNull(message = "标签不能为空")
    private Map<String, String> tags;

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
