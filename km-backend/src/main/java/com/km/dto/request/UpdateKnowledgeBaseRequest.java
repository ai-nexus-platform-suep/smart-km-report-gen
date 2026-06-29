package com.km.dto.request;

import java.util.Map;

public class UpdateKnowledgeBaseRequest {
    private String name;
    private String description;
    private Map<String, Object> chunkStrategy;
    private String searchStrategy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(Map<String, Object> chunkStrategy) { this.chunkStrategy = chunkStrategy; }
    public String getSearchStrategy() { return searchStrategy; }
    public void setSearchStrategy(String searchStrategy) { this.searchStrategy = searchStrategy; }
}
