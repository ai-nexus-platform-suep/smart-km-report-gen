package com.km.dto.response;

public class StatsSummaryVO {
    private int knowledgeBaseCount;
    private int documentCount;
    private int chunkCount;
    private int readyDocumentCount;

    public int getKnowledgeBaseCount() { return knowledgeBaseCount; }
    public void setKnowledgeBaseCount(int knowledgeBaseCount) { this.knowledgeBaseCount = knowledgeBaseCount; }
    public int getDocumentCount() { return documentCount; }
    public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public int getReadyDocumentCount() { return readyDocumentCount; }
    public void setReadyDocumentCount(int readyDocumentCount) { this.readyDocumentCount = readyDocumentCount; }
}
