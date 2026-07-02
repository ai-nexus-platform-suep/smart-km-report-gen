package com.km.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class StatsSummaryVO {

    @JsonProperty("knowledgeBaseCount")
    private long knowledgeBaseCount;

    @JsonProperty("documentCount")
    private long documentCount;

    @JsonProperty("chunkCount")
    private long chunkCount;

    @JsonProperty("processingDocumentCount")
    private long processingDocumentCount;

    @JsonProperty("failedDocumentCount")
    private long failedDocumentCount;

    @JsonProperty("readyDocumentCount")
    private long readyDocumentCount;

    @JsonProperty("dailyUploadTrend")
    private List<Map<String, Object>> dailyUploadTrend;

    public long getKnowledgeBaseCount() { return knowledgeBaseCount; }
    public void setKnowledgeBaseCount(long v) { this.knowledgeBaseCount = v; }
    public long getDocumentCount() { return documentCount; }
    public void setDocumentCount(long v) { this.documentCount = v; }
    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long v) { this.chunkCount = v; }
    public long getProcessingDocumentCount() { return processingDocumentCount; }
    public void setProcessingDocumentCount(long v) { this.processingDocumentCount = v; }
    public long getFailedDocumentCount() { return failedDocumentCount; }
    public void setFailedDocumentCount(long v) { this.failedDocumentCount = v; }
    public long getReadyDocumentCount() { return readyDocumentCount; }
    public void setReadyDocumentCount(long v) { this.readyDocumentCount = v; }
    public List<Map<String, Object>> getDailyUploadTrend() { return dailyUploadTrend; }
    public void setDailyUploadTrend(List<Map<String, Object>> v) { this.dailyUploadTrend = v; }
}
