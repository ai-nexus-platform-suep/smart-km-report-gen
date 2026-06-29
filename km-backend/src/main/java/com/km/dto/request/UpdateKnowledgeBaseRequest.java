package com.km.dto.request;

import javax.validation.constraints.Size;

public class UpdateKnowledgeBaseRequest {
    @Size(max = 100)
    private String name;
    @Size(max = 500)
    private String description;
    private String chunkStrategy;
    private String searchStrategy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; }
    public String getSearchStrategy() { return searchStrategy; }
    public void setSearchStrategy(String searchStrategy) { this.searchStrategy = searchStrategy; }
}
