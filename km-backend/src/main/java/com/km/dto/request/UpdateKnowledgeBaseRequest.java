package com.km.dto.request;

import javax.validation.constraints.Size;
import java.util.Map;

public class UpdateKnowledgeBaseRequest {
    @Size(max = 100)
    private String name;
    @Size(max = 500)
    private String description;
    private String docType;
    private Map<String, Object> chunkStrategy;
    private String searchStrategy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public Map<String, Object> getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(Map<String, Object> chunkStrategy) { this.chunkStrategy = chunkStrategy; }
    public String getSearchStrategy() { return searchStrategy; }
    public void setSearchStrategy(String searchStrategy) { this.searchStrategy = searchStrategy; }
}
