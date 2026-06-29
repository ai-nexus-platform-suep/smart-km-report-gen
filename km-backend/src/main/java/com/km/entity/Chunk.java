package com.km.entity;

import java.time.LocalDateTime;

public class Chunk {
    private String id;
    private String docId;
    private String content;
    private String chapterPath;
    private Integer chunkIndex;
    private String chunkType;
    private String vectorId;
    private Integer charCount;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getChapterPath() { return chapterPath; }
    public void setChapterPath(String chapterPath) { this.chapterPath = chapterPath; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }
    public String getVectorId() { return vectorId; }
    public void setVectorId(String vectorId) { this.vectorId = vectorId; }
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
