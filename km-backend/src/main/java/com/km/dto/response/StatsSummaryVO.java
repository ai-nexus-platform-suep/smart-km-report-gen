package com.km.dto.response;

import java.util.List;
import java.util.Map;

public class StatsSummaryVO {
    private long kbCount;
    private long docCount;
    private long chunkCount;
    private List<Map<String, Object>> dailyUploadTrend;

    public long getKbCount() { return kbCount; }
    public void setKbCount(long kbCount) { this.kbCount = kbCount; }
    public long getDocCount() { return docCount; }
    public void setDocCount(long docCount) { this.docCount = docCount; }
    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long chunkCount) { this.chunkCount = chunkCount; }
    public List<Map<String, Object>> getDailyUploadTrend() { return dailyUploadTrend; }
    public void setDailyUploadTrend(List<Map<String, Object>> dailyUploadTrend) { this.dailyUploadTrend = dailyUploadTrend; }
}
