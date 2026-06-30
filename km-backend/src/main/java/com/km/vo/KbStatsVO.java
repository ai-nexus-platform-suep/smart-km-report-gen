package com.km.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KbStatsVO {

    @JsonProperty("kbId")
    private String kbId;

    @JsonProperty("kbName")
    private String kbName;

    @JsonProperty("documentCount")
    private long documentCount;

    @JsonProperty("chunkCount")
    private long chunkCount;

    public String getKbId() { return kbId; }
    public void setKbId(String v) { this.kbId = v; }
    public String getKbName() { return kbName; }
    public void setKbName(String v) { this.kbName = v; }
    public long getDocumentCount() { return documentCount; }
    public void setDocumentCount(long v) { this.documentCount = v; }
    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long v) { this.chunkCount = v; }
}
