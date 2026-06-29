package com.km.dto.request;

import java.util.Map;

public class DocumentTagsRequest {
    private Map<String, String> tags;

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
